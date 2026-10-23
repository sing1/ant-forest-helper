package com.sing1.apply;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements LogBus.Listener {

    private TextView permValue;
    private TextView cfgValue;
    private TextView logView;
    private ScrollView logScroll;

    private final StringBuilder logBuf = new StringBuilder();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            ui.postDelayed(this, 800);
        }
    };

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Notifier.init(this);
        setContentView(buildUi());
        LogBus.add(this);
        for (String s : LogBus.history()) logBuf.append(s).append('\n');
        logView.setText(logBuf.toString());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        ui.post(ticker);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(ticker);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        LogBus.remove(this);
        ui.removeCallbacks(ticker);
        super.onDestroy();
    }

    // ---------------- UI ----------------

    private View buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(0xFFF2F4F7);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(26), dp(20), dp(24));
        sv.addView(root, new ViewGroup.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("森林助手");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF111827);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("一键收能量 · 能量雨");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        sub.setTextColor(0xFF6B7280);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(6);
        subLp.bottomMargin = dp(16);
        root.addView(sub, subLp);

        // 权限状态条（点进去看详情）
        LinearLayout permBar = new LinearLayout(this);
        permBar.setOrientation(LinearLayout.HORIZONTAL);
        permBar.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable permBg = new GradientDrawable();
        permBg.setColor(Color.WHITE);
        permBg.setCornerRadius(dp(14));
        permBar.setBackground(permBg);
        permBar.setPadding(dp(16), dp(14), dp(16), dp(14));
        permBar.setOnClickListener(v -> startActivity(new Intent(this, PermissionsActivity.class)));

        TextView permLabel = new TextView(this);
        permLabel.setText("权限");
        permLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        permLabel.setTextColor(0xFF111827);
        permBar.addView(permLabel, new LinearLayout.LayoutParams(0, -2, 1f));

        permValue = new TextView(this);
        permValue.setText("已就绪");
        permValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        permBar.addView(permValue);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        arrow.setTextColor(0xFF9CA3AF);
        arrow.setPadding(dp(10), 0, 0, 0);
        permBar.addView(arrow);

        root.addView(permBar, new LinearLayout.LayoutParams(-1, -2));

        // 主按钮
        Button collectBtn = new Button(this);
        collectBtn.setAllCaps(false);
        collectBtn.setText("一键收能量");
        collectBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        collectBtn.setTypeface(Typeface.DEFAULT_BOLD);
        collectBtn.setTextColor(Color.WHITE);
        GradientDrawable cb = new GradientDrawable();
        cb.setColor(0xFF1FA85C);
        cb.setCornerRadius(dp(18));
        collectBtn.setBackground(cb);
        collectBtn.setStateListAnimator(null);
        collectBtn.setOnClickListener(v -> startCollect());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, dp(56));
        clp.topMargin = dp(14);
        root.addView(collectBtn, clp);

        Button rainBtn = new Button(this);
        rainBtn.setAllCaps(false);
        rainBtn.setText("能量雨");
        rainBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        rainBtn.setTypeface(Typeface.DEFAULT_BOLD);
        rainBtn.setTextColor(0xFF0F766E);
        GradientDrawable rb = new GradientDrawable();
        rb.setColor(0xFFE6FFFA);
        rb.setCornerRadius(dp(18));
        rb.setStroke(dp(1), 0xFF99F6E4);
        rainBtn.setBackground(rb);
        rainBtn.setStateListAnimator(null);
        rainBtn.setOnClickListener(v -> startRain());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, dp(56));
        rlp.topMargin = dp(12);
        root.addView(rainBtn, rlp);

        // 设置入口
        LinearLayout cfgRow = new LinearLayout(this);
        cfgRow.setOrientation(LinearLayout.HORIZONTAL);
        cfgRow.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable cfgBg = new GradientDrawable();
        cfgBg.setColor(Color.WHITE);
        cfgBg.setCornerRadius(dp(14));
        cfgRow.setBackground(cfgBg);
        cfgRow.setPadding(dp(16), dp(13), dp(16), dp(13));
        cfgRow.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        TextView cfgLabel = new TextView(this);
        cfgLabel.setText("能量雨设置");
        cfgLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        cfgLabel.setTextColor(0xFF111827);
        cfgRow.addView(cfgLabel, new LinearLayout.LayoutParams(0, -2, 1f));

        cfgValue = new TextView(this);
        cfgValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        cfgValue.setTextColor(0xFF6B7280);
        cfgRow.addView(cfgValue);

        TextView arrow2 = new TextView(this);
        arrow2.setText("›");
        arrow2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        arrow2.setTextColor(0xFF9CA3AF);
        arrow2.setPadding(dp(10), 0, 0, 0);
        cfgRow.addView(arrow2);

        LinearLayout.LayoutParams cfgLp = new LinearLayout.LayoutParams(-1, -2);
        cfgLp.topMargin = dp(12);
        root.addView(cfgRow, cfgLp);

        // 日志
        LinearLayout logHead = new LinearLayout(this);
        logHead.setOrientation(LinearLayout.HORIZONTAL);
        logHead.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lhlp = new LinearLayout.LayoutParams(-1, -2);
        lhlp.topMargin = dp(20);
        lhlp.bottomMargin = dp(8);
        root.addView(logHead, lhlp);

        TextView logTitle = new TextView(this);
        logTitle.setText("运行日志");
        logTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        logTitle.setTypeface(Typeface.DEFAULT_BOLD);
        logTitle.setTextColor(0xFF374151);
        logHead.addView(logTitle, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView clear = new TextView(this);
        clear.setText("清空");
        clear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        clear.setTextColor(0xFF6B7280);
        clear.setPadding(dp(12), dp(4), dp(4), dp(4));
        clear.setOnClickListener(v -> {
            LogBus.clear();
            logBuf.setLength(0);
            logView.setText("");
        });
        logHead.addView(clear);

        logScroll = new ScrollView(this);
        GradientDrawable logBg = new GradientDrawable();
        logBg.setColor(0xFF0F172A);
        logBg.setCornerRadius(dp(14));
        logScroll.setBackground(logBg);
        logScroll.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(logScroll, new LinearLayout.LayoutParams(-1, dp(250)));

        logView = new TextView(this);
        logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        logView.setTextColor(0xFFD1D5DB);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setLineSpacing(dp(3), 1f);
        logScroll.addView(logView);

        TextView tip = new TextView(this);
        tip.setText("小米/澎湃：请在「应用设置 → 森林助手」允许自启动、设为省电无限制，否则后台可能被杀。");
        tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tip.setTextColor(0xFF9CA3AF);
        tip.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = dp(14);
        root.addView(tip, tlp);

        return sv;
    }

    // ---------------- 行为 ----------------

    private void startCollect() {
        if (!Perm.accessibility(this)) {
            toast("请先开启无障碍服务");
            startActivity(new Intent(this, PermissionsActivity.class));
            return;
        }
        ForestAccessibilityService s = ForestAccessibilityService.INSTANCE;
        if (s == null) {
            toast("服务还没连上，请稍后再试");
            return;
        }
        if (!openAlipay()) return;
        toast("开始收取，请不要锁屏");
        LogBus.log("点击一键收能量，正在打开蚂蚁森林…");
        ui.postDelayed(s::startRun, 900);
    }

    private void startRain() {
        if (!Perm.accessibility(this)) {
            toast("请先开启无障碍服务");
            startActivity(new Intent(this, PermissionsActivity.class));
            return;
        }
        ForestAccessibilityService s = ForestAccessibilityService.INSTANCE;
        if (s == null) {
            toast("服务还没连上，请稍后再试");
            return;
        }
        if (!openAlipay()) return;
        toast("进入能量雨后，用左侧悬浮窗操作");
        LogBus.log("点击能量雨，正在打开蚂蚁森林…");
        ui.postDelayed(s::startRainFlow, 900);
    }

    private boolean openAlipay() {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(ForestAccessibilityService.ALIPAY_FOREST_URI));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (Exception e) {
            toast("打开支付宝失败：" + e.getMessage());
            return false;
        }
    }

    private void refresh() {
        RainConfig.load(this);
        boolean acc = Perm.accessibility(this);
        boolean nf = Perm.notification(this);
        if (acc && nf) {
            permValue.setText("已就绪");
            permValue.setTextColor(0xFF16A34A);
        } else {
            int miss = (acc ? 0 : 1) + (nf ? 0 : 1);
            permValue.setText(miss + " 项未开启");
            permValue.setTextColor(0xFFDC2626);
        }
        cfgValue.setText(RainConfig.seconds + " 秒 · " + rowName() + " · " + RainConfig.speedName());
    }

    private String rowName() {
        if (RainConfig.rowRatio < 0.26f) return "偏高";
        if (RainConfig.rowRatio > 0.31f) return "中间";
        return "中上";
    }

    @Override
    public void onLog(final String line) {
        logBuf.append(line).append('\n');
        if (logBuf.length() > 30000) logBuf.delete(0, logBuf.length() - 30000);
        logView.setText(logBuf.toString());
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
