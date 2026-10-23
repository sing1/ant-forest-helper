package com.sing1.apply;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 能量雨悬浮小窗：半透明、贴在屏幕左侧。
 *
 * 空闲态： [开始] [关闭]
 * 运行态： 倒计时  [暂停]
 */
public final class RainHud {

    public interface Listener {
        void onStart();

        void onPause();

        void onReopen();

        void onClose();
    }

    private final Context ctx;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());

    private WindowManager wm;
    private LinearLayout box;
    private TextView countView;
    private TextView hintView;
    private Button startBtn;
    private Button pauseBtn;
    private Button reopenBtn;
    private Button closeBtn;
    private boolean attached;
    private boolean running;

    RainHud(Context ctx, Listener l) {
        this.ctx = ctx;
        this.listener = l;
    }

    private int dp(float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    public void show() {
        main.post(this::attach);
    }

    public void hide() {
        main.post(this::detach);
    }

    public void setRunning(boolean r) {
        main.post(() -> applyRunning(r));
    }

    public void setCountdown(int sec) {
        main.post(() -> {
            if (countView != null) countView.setText(String.valueOf(sec));
        });
    }

    public void setHint(String text) {
        main.post(() -> {
            if (hintView != null) {
                hintView.setText(text);
                hintView.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
            }
        });
    }

    private void attach() {
        if (attached) return;
        // 1) 无障碍浮层（不需要额外权限），必须先绑定到具体屏幕才能拿到窗口 token
        try {
            android.hardware.display.DisplayManager dm =
                    (android.hardware.display.DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
            android.view.Display display = dm == null ? null : dm.getDisplay(android.view.Display.DEFAULT_DISPLAY);
            if (display != null) {
                Context displayCtx = ctx.createDisplayContext(display);
                Context windowCtx = displayCtx.createWindowContext(
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null);
                if (attachWith(windowCtx, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)) {
                    attached = true;
                    LogBus.log("能量雨悬浮窗已显示（无障碍浮层）");
                    return;
                }
            }
        } catch (Throwable t) {
            LogBus.log("无障碍浮层失败: " + t);
        }
        // 2) 保底：应用悬浮窗，需要「显示在其他应用上层」权限
        if (Settings.canDrawOverlays(ctx)
                && attachWith(ctx, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)) {
            attached = true;
            LogBus.log("能量雨悬浮窗已显示（应用浮层）");
            return;
        }
        LogBus.log("✗ 悬浮窗无法显示：请到系统设置里给「森林助手」开启「显示悬浮窗/后台弹出界面」权限");
        toast("悬浮窗需要「显示在其他应用上层」权限");
    }

    private boolean attachWith(Context c, int type) {
        try {
            WindowManager w = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
            if (w == null) return false;

            LinearLayout root = new LinearLayout(c);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(9), dp(9), dp(9), dp(9));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xB812161C); // 半透明深色
            bg.setCornerRadius(dp(14));
            bg.setStroke(dp(1), 0x40FFFFFF);
            root.setBackground(bg);

            TextView title = new TextView(c);
            title.setText("能量雨");
            title.setTextSize(11);
            title.setTextColor(0xCCE5E7EB);
            title.setGravity(Gravity.CENTER);
            root.addView(title);

            countView = new TextView(c);
            countView.setTextSize(26);
            countView.setTextColor(Color.WHITE);
            countView.setGravity(Gravity.CENTER);
            countView.setVisibility(View.GONE);
            root.addView(countView);

            hintView = new TextView(c);
            hintView.setTextSize(9);
            hintView.setTextColor(0xFFFFD166);
            hintView.setGravity(Gravity.CENTER);
            hintView.setVisibility(View.GONE);
            root.addView(hintView);

            startBtn = mkButton(c, "开始", 0xFF1FA85C);
            startBtn.setOnClickListener(v -> listener.onStart());
            root.addView(startBtn);

            pauseBtn = mkButton(c, "暂停", 0xFFDC2626);
            pauseBtn.setOnClickListener(v -> listener.onPause());
            pauseBtn.setVisibility(View.GONE);
            root.addView(pauseBtn);

            reopenBtn = mkButton(c, "打开能量雨", 0xFF2563EB);
            reopenBtn.setOnClickListener(v -> listener.onReopen());
            root.addView(reopenBtn);

            closeBtn = mkButton(c, "关闭", 0x33FFFFFF);
            closeBtn.setOnClickListener(v -> listener.onClose());
            root.addView(closeBtn);

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    dp(94),
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            lp.x = dp(2);
            lp.y = dp(60);

            w.addView(root, lp);
            box = root;
            wm = w;
            applyRunning(running);
            return true;
        } catch (Throwable t) {
            LogBus.log("悬浮窗 addView 失败(type=" + type + "): " + t);
            return false;
        }
    }

    private Button mkButton(Context c, String text, int color) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(Color.WHITE);
        b.setPadding(0, 0, 0, 0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(9));
        b.setBackground(d);
        b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(34));
        lp.topMargin = dp(7);
        b.setLayoutParams(lp);
        return b;
    }

    private void applyRunning(boolean r) {
        running = r;
        if (box == null) return;
        startBtn.setVisibility(r ? View.GONE : View.VISIBLE);
        reopenBtn.setVisibility(r ? View.GONE : View.VISIBLE);
        closeBtn.setVisibility(r ? View.GONE : View.VISIBLE);
        pauseBtn.setVisibility(r ? View.VISIBLE : View.GONE);
        countView.setVisibility(r ? View.VISIBLE : View.GONE);
    }

    private void detach() {
        if (!attached) return;
        try {
            wm.removeView(box);
        } catch (Throwable ignored) {
        }
        attached = false;
        box = null;
        LogBus.log("能量雨悬浮窗已关闭");
    }

    private void toast(String s) {
        main.post(() -> android.widget.Toast.makeText(ctx, s, android.widget.Toast.LENGTH_LONG).show());
    }
}
