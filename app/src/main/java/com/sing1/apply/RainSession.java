package com.sing1.apply;

import android.view.accessibility.AccessibilityNodeInfo;

/**
 * 能量雨流程：
 *   进入能量雨页面 → 弹出半透明悬浮窗（开始/关闭）
 *   点「开始」 → 若还在落地页先点「立即开启」→ 固定在中上部一行疯狂点击 20 秒（悬浮窗显示倒计时）
 *   点「暂停」 → 立即停止点击，恢复 开始/关闭
 *   点「关闭」 → 退出蚂蚁森林并回到 App
 *   过程中检测到支付宝风控验证 → 自动停止，提示用户手动验证
 */
final class RainSession {

    private static final int COLUMNS = 9;

    private final ForestAccessibilityService svc;
    private volatile RainHud hud;
    private volatile boolean tapping = false;

    RainSession(ForestAccessibilityService svc) {
        this.svc = svc;
    }

    // ---------------- 入口 ----------------

    void start() {
        new Thread(this::navigate, "rain-nav").start();
    }

    /** 只弹悬浮窗，不导航（调试/预览） */
    void preview() {
        ensureHud();
        hud.show();
    }

    void destroy() {
        tapping = false;
        if (hud != null) hud.hide();
    }

    private void ensureHud() {
        if (hud != null) return;
        hud = new RainHud(svc, new RainHud.Listener() {
            @Override
            public void onStart() {
                if (!tapping) new Thread(RainSession.this::roundWorker, "rain-tap").start();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onReopen() {
                reopen();
            }

            @Override
            public void onClose() {
                closeAndReturn();
            }
        });
    }

    private void navigate() {
        LogBus.log("能量雨：准备进入…");
        if (svc.findText("立即开启") != null) {
            LogBus.log("已经在能量雨页面，直接弹出悬浮窗");
            ensureHud();
            hud.show();
            return;
        }
        long end = System.currentTimeMillis() + 25000;
        AccessibilityNodeInfo entry = null;
        long lastLog = 0;
        while (System.currentTimeMillis() < end) {
            if (svc.isRiskControlPage()) {
                LogBus.log("✗ 打开时遇到支付宝验证页");
                svc.toast("请先手动完成支付宝验证");
                return;
            }
            entry = svc.findText("能量雨");
            if (entry != null) break;
            if (System.currentTimeMillis() - lastLog > 3000) {
                lastLog = System.currentTimeMillis();
                LogBus.log("等待能量雨入口… " + svc.describeForeground());
            }
            sleep(700);
        }
        if (entry == null) {
            LogBus.log("✗ 没找到「能量雨」入口");
            svc.toast("没找到能量雨入口，请确认支付宝停在蚂蚁森林首页");
            return;
        }
        LogBus.log("✓ 找到「能量雨」入口，点击");
        svc.clickNode(entry);

        end = System.currentTimeMillis() + 15000;
        AccessibilityNodeInfo lobby = null;
        while (System.currentTimeMillis() < end) {
            if (svc.isRiskControlPage()) {
                LogBus.log("✗ 进入能量雨时触发风控");
                svc.toast("触发了支付宝验证，手动完成后再点悬浮窗「开始」");
                ensureHud();
                hud.show();
                hud.setHint("请先手动验证");
                return;
            }
            lobby = svc.findText("立即开启");
            if (lobby != null) break;
            sleep(600);
        }
        if (lobby == null) {
            LogBus.log("✗ 没进入能量雨页面");
            svc.toast("没进入能量雨页面");
            return;
        }
        LogBus.log("✓ 已进入能量雨页面，弹出悬浮窗");
        ensureHud();
        hud.show();
        hud.setHint("");
    }

    // ---------------- 开始 / 暂停 ----------------

    private void roundWorker() {
        tapping = true;
        try {
            AccessibilityNodeInfo go = svc.findText("立即开启");
            if (go != null) {
                LogBus.log("点「立即开启」");
                svc.clickNode(go);
                sleep(2600);
            }
            if (svc.isRiskControlPage()) {
                LogBus.log("✗ 触发风控，已停止，请手动完成验证后再点「开始」");
                svc.toast("触发风控，请手动完成验证");
                hud.setHint("请先手动验证");
                return;
            }
            hud.setHint("");
            hud.setRunning(true);
            tapLoop();
        } catch (Throwable t) {
            LogBus.log("能量雨异常: " + t);
        } finally {
            tapping = false;
            if (hud != null) {
                hud.setRunning(false);
                hud.setCountdown(RainConfig.seconds);
            }
        }
    }

    private void tapLoop() {
        int[] wh = svc.screenSize();
        int w = wh[0];
        int h = wh[1];
        float y = h * RainConfig.rowRatio;
        int columns = COLUMNS;
        float left = 0.08f;
        float span = 0.84f;
        float[] xs = new float[columns];
        for (int i = 0; i < columns; i++) {
            xs[i] = w * (left + span * i / (columns - 1));
        }

        int seconds = RainConfig.seconds;
        int dur = RainConfig.tapDuration();
        int gap = RainConfig.tapGap();

        long end = System.currentTimeMillis() + seconds * 1000L;
        int shown = -1;
        int taps = 0;
        long lastRiskCheck = 0;
        LogBus.log(String.format("开始疯狂点击：%d 秒，y=%d，%s档(%dms/%dms)",
                seconds, Math.round(y), RainConfig.speedName(), dur, gap));

        while (tapping) {
            long now = System.currentTimeMillis();
            if (now >= end) break;
            int remain = (int) Math.ceil((end - now) / 1000.0);
            if (remain != shown) {
                shown = remain;
                hud.setCountdown(Math.max(0, remain));
            }
            if (RainConfig.multi()) {
                // 一次手势在 9 个点同时按下
                svc.tapMultiAsync(xs, y, dur);
                taps += xs.length;
                try {
                    Thread.sleep(gap);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } else {
                for (float x : xs) {
                    if (!tapping || System.currentTimeMillis() >= end) break;
                    svc.tapAsync(x, y, dur);
                    taps++;
                    try {
                        Thread.sleep(gap);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            if (System.currentTimeMillis() - lastRiskCheck > 2500) {
                lastRiskCheck = System.currentTimeMillis();
                if (svc.isRiskControlPage()) {
                    LogBus.log("✗ 点击过程中出现风控验证，自动停止（已点 " + taps + " 次）");
                    svc.toast("触发风控，请手动完成验证");
                    hud.setHint("请先手动验证");
                    return;
                }
            }
        }
        long secs = Math.max(1, Math.round((System.currentTimeMillis() - (end - seconds * 1000L)) / 1000.0));
        LogBus.log("本轮点击结束：共 " + taps + " 次，约 " + (taps / secs) + " 次/秒");
    }

    /** 用户误关掉能量雨页面后，重新拉起支付宝并回到能量雨页 */
    private void reopen() {
        tapping = false;
        if (hud != null) {
            hud.setRunning(false);
            hud.setHint("正在打开…");
        }
        new Thread(() -> {
            boolean ok = svc.openAlipayForest();
            LogBus.log(ok ? "重新拉起支付宝，返回能量雨页面…" : "✗ 拉起支付宝失败，请手动切回支付宝");
            sleep(3800);
            navigate();
        }, "rain-reopen").start();
    }

    private void pause() {
        tapping = false;
        if (hud != null) {
            hud.setRunning(false);
            hud.setCountdown(RainConfig.seconds);
        }
        LogBus.log("已暂停");
    }

    // ---------------- 关闭 ----------------

    private void closeAndReturn() {
        tapping = false;
        if (hud != null) hud.hide();
        new Thread(() -> {
            LogBus.log("关闭：退出支付宝");
            for (int i = 0; i < 5; i++) {
                svc.back();
                sleep(650);
                String pkg = svc.foregroundPackage();
                if (pkg != null && !"com.eg.android.AlipayGphone".equals(pkg)) {
                    LogBus.log("已退出支付宝（前台：" + pkg + "）");
                    if (!"com.sing1.apply".equals(pkg)) svc.bringAppToFront();
                    return;
                }
            }
            LogBus.log("仍在支付宝，直接拉起本 App");
            svc.bringAppToFront();
        }, "rain-close").start();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
