package com.sing1.apply;

/**
 * 收取流程状态机：
 *
 *   打开蚂蚁森林 → 点「找能量」 → 循环：
 *       [识别到亮的「一键收」] → 点一次 → 再点一次 → 上划
 *       [识别不到 / 已收完]     → 上划，连续 3 次就结束
 *   → 返回键关闭蚂蚁森林 → 完成
 */
final class Runner implements Runnable {

    private static final int MISS_LIMIT = 3;      // 连续几次上划都没能量就结束
    private static final int MAX_PAGES = 60;      // 安全上限，防止死循环
    private static final float THRESHOLD = 0.65f; // 识别阈值

    // 屏幕比例 ROI（右下方，按钮所在区域）
    private static final float[] ROI_COLLECT = {0.70f, 0.50f, 1.00f, 0.63f};
    private static final float[] ROI_FIND = {0.65f, 0.60f, 1.00f, 0.70f};

    private static volatile boolean stopFlag = false;
    private static volatile boolean running = false;

    static boolean isRunning() {
        return running;
    }

    static void requestStop() {
        stopFlag = true;
    }

    private final ForestAccessibilityService svc;
    private float scale = 0.5f;
    private int screenW = 1080;
    private int screenH = 2400;
    private int collectedPages = 0;
    private String summary = "已停止";

    Runner(ForestAccessibilityService svc) {
        this.svc = svc;
    }

    @Override
    public void run() {
        running = true;
        stopFlag = false;
        try {
            int[] wh = svc.screenSize();
            screenW = wh[0];
            screenH = wh[1];
            scale = 0.5f * screenW / Matcher.REF_WIDTH;
            svc.initTemplates();
            exec();
        } catch (Throwable t) {
            LogBus.log("✗ 运行异常: " + t);
            summary = "运行异常：" + t.getClass().getSimpleName();
        } finally {
            running = false;
            Notifier.finish(svc, summary);
            LogBus.log("— " + summary + " —");
        }
    }

    private void exec() {
        if (!svc.isInteractive()) {
            summary = "屏幕未点亮，请点亮并解锁后重试";
            LogBus.log("✗ 屏幕是熄灭的，无法截图");
            return;
        }
        LogBus.log("开始执行，屏幕 " + screenW + "x" + screenH);
        Notifier.progress(svc, "正在进入蚂蚁森林…");

        // 1. 等蚂蚁森林出现，点「找能量」
        LogBus.log("① 等待蚂蚁森林界面…");
        Matcher.Hit find = waitFor(svc.tplFind(), ROI_FIND, 30000);
        if (stopFlag) {
            summary = "已被手动停止";
            return;
        }
        if (find == null) {
            summary = "未识别到蚂蚁森林，请确认支付宝已登录";
            LogBus.log("✗ 30 秒内没识别到「找能量」。可能原因：支付宝未登录 / 停在别的页面 / 网络太慢");
            return;
        }
        LogBus.log(String.format("✓ 识别到「找能量」(%.3f)，点击", find.score));
        svc.tapSync(find.cx / scale, find.cy / scale);
        sleep(2200);
        settle(2500);

        // 2. 循环收取
        for (int page = 1; page <= MAX_PAGES && !stopFlag; page++) {
            Matcher.Prepared shot = shot();
            Btn btn = shot == null ? null : findCollectBtn(shot);

            if (btn != null && btn.bright) {
                missRun = 0; // 收到能量就清零：只有"连续"收不到才算没能量
                LogBus.log(String.format("第 %d 页：识别到「一键收」(%.3f)，收取", page, btn.score));
                svc.tapSync(btn.x, btn.y);
                sleep(1100);

                // 用户要求：同一页收两次，尽量吃干净
                Matcher.Prepared shot2 = shot();
                Btn again = shot2 == null ? null : findCollectBtn(shot2);
                if (again != null && again.bright) {
                    LogBus.log("第 " + page + " 页：再收一次");
                    svc.tapSync(again.x, again.y);
                    sleep(900);
                }
                collectedPages++;
                Notifier.progress(svc, "已收取 " + collectedPages + " 个好友的能量…");
            } else {
                missRun++;
                LogBus.log(String.format("第 %d 页：没有可收的能量（连续 %d 次）", page, missRun));
                if (missRun >= MISS_LIMIT) {
                    LogBus.log("→ 连续 " + MISS_LIMIT + " 次都识别不到可收的「一键收」，判定收取完毕");
                    break;
                }
            }

            if (stopFlag) break;

            LogBus.log("↑ 上划，找下一位好友");
            svc.swipeSync(screenW * 0.5f, screenH * 0.79f, screenW * 0.5f, screenH * 0.25f, 320);
            sleep(1400);
            settle(2000);
        }

        // 3. 关闭蚂蚁森林
        LogBus.log("② 关闭蚂蚁森林页面");
        closeForest();

        if (stopFlag) {
            summary = "已停止，共收 " + collectedPages + " 位好友";
        } else {
            summary = "收取完毕：共 " + collectedPages + " 位好友有能量";
        }
    }

    /**
     * 退出蚂蚁森林：按一次返回后确认真的离开了森林页面，还在才再按。
     * 避免无脑按两次把支付宝也退到桌面。
     */
    private void closeForest() {
        for (int i = 0; i < 3; i++) {
            svc.back();
            sleep(900);
            Matcher.Prepared p = shot();
            if (p == null) return;
            boolean stillForest = findCollectBtn(p) != null || isVisible(p, svc.tplFind(), ROI_FIND);
            if (!stillForest) {
                LogBus.log("已退出蚂蚁森林");
                return;
            }
            LogBus.log("仍在蚂蚁森林，再按一次返回");
        }
    }

    private boolean isVisible(Matcher.Prepared p, Matcher.Tpl tpl, float[] roi) {
        Matcher.Hit h = Matcher.find(p, tpl, roi[0], roi[1], roi[2], roi[3], 2);
        return h != null && h.score >= THRESHOLD;
    }

    /** 连续 3 次 miss 的计数器 */
    private int missRun = 0;

    private static final class Btn {
        final boolean bright;
        final float x;
        final float y;
        final float score;

        Btn(boolean bright, float x, float y, float score) {
            this.bright = bright;
            this.x = x;
            this.y = y;
            this.score = score;
        }
    }

    private Matcher.Prepared shot() {
        android.graphics.Bitmap full = svc.screenshotSync(5000);
        if (full == null) return null;
        if (full.getWidth() != screenW) {
            screenW = full.getWidth();
            screenH = full.getHeight();
            scale = 0.5f * screenW / Matcher.REF_WIDTH;
            svc.initTemplates();
        }
        Matcher.Prepared p = Matcher.prepare(full, scale);
        full.recycle();
        return p;
    }

    private Btn findCollectBtn(Matcher.Prepared p) {
        Matcher.Hit bright = Matcher.find(p, svc.tplCollectBright(), ROI_COLLECT[0], ROI_COLLECT[1], ROI_COLLECT[2], ROI_COLLECT[3], 2);
        Matcher.Hit dim = Matcher.find(p, svc.tplCollectDim(), ROI_COLLECT[0], ROI_COLLECT[1], ROI_COLLECT[2], ROI_COLLECT[3], 2);
        boolean hasBright = bright != null && bright.score >= THRESHOLD;
        boolean hasDim = dim != null && dim.score >= THRESHOLD;
        if (hasBright && (!hasDim || bright.score >= dim.score)) {
            return new Btn(true, bright.cx / scale, bright.cy / scale, bright.score);
        }
        if (hasDim) {
            return new Btn(false, dim.cx / scale, dim.cy / scale, dim.score);
        }
        return null;
    }

    private Matcher.Hit waitFor(Matcher.Tpl tpl, float[] roi, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end && !stopFlag) {
            Matcher.Prepared p = shot();
            if (p != null) {
                Matcher.Hit h = Matcher.find(p, tpl, roi[0], roi[1], roi[2], roi[3], 2);
                if (h != null && h.score >= THRESHOLD) return h;
            }
            sleep(700);
        }
        return null;
    }

    /** 等页面动画停下来，避免在过渡动画中截图识别 */
    private void settle(long maxMs) {
        long end = System.currentTimeMillis() + maxMs;
        Matcher.Prepared prev = null;
        while (System.currentTimeMillis() < end && !stopFlag) {
            Matcher.Prepared cur = shot();
            if (cur != null && prev != null && Matcher.diff(prev, cur) < 6f) return;
            prev = cur;
            sleep(500);
        }
    }

    private void sleep(long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end && !stopFlag) {
            try {
                Thread.sleep(Math.min(150, Math.max(10, end - System.currentTimeMillis())));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
