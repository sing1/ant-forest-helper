package com.sing1.apply;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 无障碍服务：提供截图（takeScreenshot）、手势注入（dispatchGesture）和返回键能力。
 * 真正的业务流程在 Runner 里。
 */
public class ForestAccessibilityService extends AccessibilityService {

    public static final String ALIPAY_FOREST_URI = "alipays://platformapi/startapp?appId=60000002";

    public static volatile ForestAccessibilityService INSTANCE;

    private final ExecutorService shotExec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        INSTANCE = this;
        LogBus.log("无障碍服务已连接");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        INSTANCE = null;
        Runner.requestStop();
        LogBus.log("无障碍服务已断开");
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不需要事件流
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        INSTANCE = null;
        super.onDestroy();
    }

    public static boolean isRunning() {
        return Runner.isRunning();
    }

    public void startRun() {
        if (Runner.isRunning()) {
            LogBus.log("已经在收取中，忽略重复启动");
            return;
        }
        new Thread(new Runner(this), "af-runner").start();
    }

    public void probe() {
        new Thread(() -> {
            try {
                initTemplates();
                Bitmap full = screenshotSync(5000);
                if (full == null) {
                    LogBus.log("测试识别：截图失败");
                    return;
                }
                float scale = 0.5f * full.getWidth() / Matcher.REF_WIDTH;
                Matcher.Prepared p = Matcher.prepare(full, scale);
                Matcher.Hit collect = Matcher.find(p, collectBright, 0.70f, 0.50f, 1f, 0.63f, 2);
                Matcher.Hit dim = Matcher.find(p, collectDim, 0.70f, 0.50f, 1f, 0.63f, 2);
                Matcher.Hit find = Matcher.find(p, findEnergy, 0.65f, 0.60f, 1f, 0.70f, 2);
                LogBus.log(String.format("测试识别：一键收=%.3f 已收=%.3f 找能量=%.3f (阈值 0.65)",
                        collect == null ? -1 : collect.score,
                        dim == null ? -1 : dim.score,
                        find == null ? -1 : find.score));
                full.recycle();
            } catch (Throwable t) {
                LogBus.log("测试识别异常: " + t);
            }
        }, "af-probe").start();
    }

    // ---------- 模板 ----------

    private volatile Matcher.Tpl collectBright, collectDim, findEnergy;
    private volatile float tplScale = 0f;

    void initTemplates() {
        float scale = 0.5f * screenSize()[0] / Matcher.REF_WIDTH;
        if (collectBright == null || Math.abs(scale - tplScale) > 1e-4f) {
            collectBright = Matcher.loadTemplate(this, "btn_collect.png", scale);
            collectDim = Matcher.loadTemplate(this, "btn_collect_dim.png", scale);
            findEnergy = Matcher.loadTemplate(this, "btn_find.png", scale);
            tplScale = scale;
            LogBus.log("识别模板已加载 (scale=" + scale + ")");
        }
    }

    Matcher.Tpl tplCollectBright() {
        initTemplates();
        return collectBright;
    }

    Matcher.Tpl tplCollectDim() {
        initTemplates();
        return collectDim;
    }

    Matcher.Tpl tplFind() {
        initTemplates();
        return findEnergy;
    }

    // ---------- 屏幕 ----------

    public int[] screenSize() {
        DisplayManager dm = getSystemService(DisplayManager.class);
        Display d = dm == null ? null : dm.getDisplay(Display.DEFAULT_DISPLAY);
        DisplayMetrics m = new DisplayMetrics();
        if (d != null) {
            d.getRealMetrics(m);
        } else {
            m = getResources().getDisplayMetrics();
        }
        return new int[]{m.widthPixels, m.heightPixels};
    }

    public boolean isInteractive() {
        PowerManager pm = getSystemService(PowerManager.class);
        return pm != null && pm.isInteractive();
    }

    private final Object shotLock = new Object();
    private long lastShotAt = 0L;
    /** 系统限制两次无障碍截图间隔必须 >= 333ms，这里留点余量 */
    private static final long SHOT_MIN_GAP_MS = 450L;

    /**
     * 带节流和失败重试的截图。
     * 触发限流后若继续高频请求，系统会一直判定"间隔过短"，所以这里必须主动放慢。
     */
    public Bitmap screenshotSync(long timeoutMs) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            synchronized (shotLock) {
                long gap = System.currentTimeMillis() - lastShotAt;
                if (gap < SHOT_MIN_GAP_MS) {
                    try {
                        Thread.sleep(SHOT_MIN_GAP_MS - gap);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
                lastShotAt = System.currentTimeMillis();
            }
            Bitmap b = shotOnce(timeoutMs);
            if (b != null) return b;
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        LogBus.log("截图连续失败，跳过本次识别");
        return null;
    }

    private Bitmap shotOnce(long timeoutMs) {
        final CountDownLatch latch = new CountDownLatch(1);
        final Bitmap[] out = new Bitmap[1];
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, shotExec, new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult result) {
                    try {
                        HardwareBuffer hb = result.getHardwareBuffer();
                        Bitmap b = Bitmap.wrapHardwareBuffer(hb, result.getColorSpace());
                        if (b != null) out[0] = b.copy(Bitmap.Config.ARGB_8888, false);
                        hb.close();
                    } catch (Throwable t) {
                        LogBus.log("截图解析异常: " + t);
                    } finally {
                        latch.countDown();
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    LogBus.log("截图失败 code=" + errorCode);
                    latch.countDown();
                }
            });
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            LogBus.log("截图调用异常: " + t);
        }
        return out[0];
    }

    // ---------- 手势 ----------

    private boolean gestureSync(GestureDescription gd) {
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] ok = new boolean[1];
        boolean posted = dispatchGesture(gd, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                ok[0] = true;
                latch.countDown();
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                ok[0] = false;
                latch.countDown();
            }
        }, main);
        if (!posted) {
            LogBus.log("手势注入被拒绝（无障碍服务可能被系统限制）");
            return false;
        }
        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ok[0];
    }

    public boolean tapSync(float x, float y) {
        Path p = new Path();
        p.moveTo(x, y);
        p.lineTo(x + 1f, y);
        GestureDescription gd = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 70))
                .build();
        return gestureSync(gd);
    }

    private final java.util.concurrent.atomic.AtomicInteger tapSent = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger tapDone = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger tapCancel = new java.util.concurrent.atomic.AtomicInteger();

    /** 不等回调的点击，用于能量雨高频连点 */
    public boolean tapAsync(float x, float y, int durationMs) {
        Path p = new Path();
        p.moveTo(x, y);
        p.lineTo(x + 1f, y);
        GestureDescription gd = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, Math.max(8, durationMs)))
                .build();
        boolean sent;
        try {
            sent = dispatchGesture(gd, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription d) {
                    tapDone.incrementAndGet();
                }

                @Override
                public void onCancelled(GestureDescription d) {
                    tapCancel.incrementAndGet();
                }
            }, main);
        } catch (Throwable t) {
            return false;
        }
        if (sent) tapSent.incrementAndGet();
        return sent;
    }

    /** 多指同时点：一次手势在多列同时按下，理论上能成倍提高命中率 */
    public boolean tapMultiAsync(float[] xs, float y, int durationMs) {
        try {
            GestureDescription.Builder b = new GestureDescription.Builder();
            for (float x : xs) {
                Path p = new Path();
                p.moveTo(x, y);
                p.lineTo(x + 1f, y);
                b.addStroke(new GestureDescription.StrokeDescription(p, 0, Math.max(8, durationMs)));
            }
            GestureDescription gd = b.build();
            boolean sent = dispatchGesture(gd, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription d) {
                    tapDone.incrementAndGet();
                }

                @Override
                public void onCancelled(GestureDescription d) {
                    tapCancel.incrementAndGet();
                }
            }, main);
            if (sent) tapSent.incrementAndGet();
            return sent;
        } catch (Throwable t) {
            LogBus.log("多指手势失败: " + t);
            return false;
        }
    }

    /** 真机测一下不同档位能跑多快，结果打进日志 */
    public void tapBenchmark() {
        new Thread(() -> {
            String pkg = foregroundPackage();
            if (!getPackageName().equals(pkg)) {
                LogBus.log("测速需要保持本 App 在前台");
                return;
            }
            int[][] presets = {{30, 35}, {20, 22}, {10, 12}, {6, 9}};
            String[] names = {"30/35", "20/22", "10/12", "6/9"};
            for (int i = 0; i < presets.length; i++) {
                int dur = presets[i][0];
                int gap = presets[i][1];
                tapSent.set(0);
                tapDone.set(0);
                tapCancel.set(0);
                long t0 = System.currentTimeMillis();
                int n = 0;
                while (System.currentTimeMillis() - t0 < 1500) {
                    tapAsync(540, 1900, dur);
                    n++;
                    try {
                        Thread.sleep(gap);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ignored) {
                }
                LogBus.log(String.format("单指 %s：%d 次/1.5s = %.1f 次/秒，完成 %d，取消 %d",
                        names[i], n, n / 1.5, tapDone.get(), tapCancel.get()));
            }

            // 多指：8 个点同时按
            float[] xs = new float[8];
            for (int i = 0; i < 8; i++) xs[i] = 100 + i * 120;
            tapSent.set(0);
            tapDone.set(0);
            tapCancel.set(0);
            long t0 = System.currentTimeMillis();
            int n = 0;
            while (System.currentTimeMillis() - t0 < 1500) {
                tapMultiAsync(xs, 1900, 25);
                n++;
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            try {
                Thread.sleep(400);
            } catch (InterruptedException ignored) {
            }
            LogBus.log(String.format("多指8点 25/30：%d 组/1.5s，完成 %d，取消 %d（每组=8 次点击）",
                    n, tapDone.get(), tapCancel.get()));
        }, "tap-bench").start();
    }

    public boolean swipeSync(float x1, float y1, float x2, float y2, int durationMs) {
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        GestureDescription gd = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, durationMs))
                .build();
        return gestureSync(gd);
    }

    public void back() {
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    /** 当前前台包名（拿不到返回 null） */
    public String foregroundPackage() {
        try {
            android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && root.getPackageName() != null) {
                return root.getPackageName().toString();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ---------------- 节点查找 / 点击 ----------------

    /** 按文本（子串）找第一个节点：自己遍历，比 findAccessibilityNodeInfosByText 稳 */
    public AccessibilityNodeInfo findText(String... keys) {
        AccessibilityNodeInfo root;
        try {
            root = getRootInActiveWindow();
        } catch (Throwable t) {
            return null;
        }
        if (root == null) return null;
        for (String k : keys) {
            try {
                AccessibilityNodeInfo n = dfsFind(root, k, 0);
                if (n != null) return n;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private AccessibilityNodeInfo dfsFind(AccessibilityNodeInfo n, String key, int depth) {
        if (n == null || depth > 40) return null;
        CharSequence t = n.getText();
        CharSequence d = n.getContentDescription();
        String s = (t != null && t.length() > 0) ? t.toString()
                : (d != null && d.length() > 0 ? d.toString() : "");
        if (s.contains(key)) return n;
        int count = n.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo c = dfsFind(n.getChild(i), key, depth + 1);
            if (c != null) return c;
        }
        return null;
    }

    /** 点击节点：优先 ACTION_CLICK，失败则按坐标点 */
    public boolean clickNode(AccessibilityNodeInfo n) {
        if (n == null) return false;
        AccessibilityNodeInfo cur = n;
        for (int i = 0; i < 6 && cur != null; i++) {
            if (cur.isClickable()) {
                try {
                    if (cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
                } catch (Throwable ignored) {
                }
                break;
            }
            try {
                cur = cur.getParent();
            } catch (Throwable t) {
                break;
            }
        }
        Rect r = new Rect();
        n.getBoundsInScreen(r);
        if (r.width() > 0 && r.height() > 0) {
            return tapSync(r.centerX(), r.centerY());
        }
        return false;
    }

    /** 是否是支付宝的风控验证页 */
    public boolean isRiskControlPage() {
        return findText("拖动滑块", "为保障您的正常访问请进行验证", "请完成安全验证") != null;
    }

    /** 诊断用：当前前台包名 + 可见文本节点数量 */
    public String describeForeground() {
        AccessibilityNodeInfo root;
        try {
            root = getRootInActiveWindow();
        } catch (Throwable t) {
            return "root异常:" + t.getClass().getSimpleName();
        }
        if (root == null) return "root=null";
        String pkg = root.getPackageName() == null ? "?" : root.getPackageName().toString();
        StringBuilder sb = new StringBuilder();
        collectTexts(root, sb, 0, 45);
        return "pkg=" + pkg + " texts=[" + sb + "]";
    }

    private int collectTexts(AccessibilityNodeInfo n, StringBuilder sb, int depth, int max) {
        if (n == null || depth > 40) return 0;
        CharSequence t = n.getText();
        CharSequence d = n.getContentDescription();
        String s = (t != null && t.length() > 0) ? t.toString() : (d != null && d.length() > 0 ? d.toString() : null);
        int count = 0;
        if (s != null) {
            if (sb.length() > 0) sb.append('|');
            sb.append(s.length() > 12 ? s.substring(0, 12) : s);
            count = 1;
            if (sb.length() > 900) return count;
        }
        for (int i = 0; i < n.getChildCount() && count < max; i++) {
            try {
                count += collectTexts(n.getChild(i), sb, depth + 1, max - count);
            } catch (Throwable ignored) {
            }
        }
        return count;
    }

    public void toast(final String msg) {
        main.post(() -> android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show());
    }

    /** 从后台拉起支付宝蚂蚁森林（悬浮窗「打开能量雨」用） */
    public boolean openAlipayForest() {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(ALIPAY_FOREST_URI));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (Throwable t) {
            LogBus.log("拉起支付宝失败: " + t);
            return false;
        }
    }

    /** 把本 App 拉到前台（关闭按钮用） */
    public void bringAppToFront() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch (Throwable t) {
            LogBus.log("回到 App 失败: " + t);
        }
    }

    // ---------------- 能量雨 ----------------

    private volatile RainSession rainSession;

    private RainSession rain() {
        if (rainSession == null) rainSession = new RainSession(this);
        return rainSession;
    }

    public void startRainFlow() {
        rain().start();
    }

    public void previewRainHud() {
        rain().preview();
    }
}
