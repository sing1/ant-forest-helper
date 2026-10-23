package com.sing1.apply;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 能量雨设置页 */
public class SettingsActivity extends Activity {

    private interface ChipPick {
        void onPick(String value);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        RainConfig.load(this);
        setContentView(build());
    }

    private View build() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(0xFFF2F4F7);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        sv.addView(root, new ViewGroup.LayoutParams(-1, -2));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(14), dp(16), dp(10));
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        back.setTextColor(0xFF111827);
        back.setPadding(dp(14), 0, dp(14), 0);
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        TextView title = new TextView(this);
        title.setText("能量雨设置");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF111827);
        bar.addView(title);
        root.addView(bar);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(4), dp(20), dp(28));
        root.addView(content);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(18));
        card.setBackground(bg);
        card.setPadding(dp(18), dp(4), dp(18), dp(4));
        content.addView(card, new LinearLayout.LayoutParams(-1, -2));

        addChipRow(card, "时长",
                new String[]{"10 秒", "15 秒", "20 秒"},
                new String[]{"10", "15", "20"},
                String.valueOf(RainConfig.seconds),
                v -> {
                    RainConfig.seconds = Integer.parseInt(v);
                    RainConfig.save(this);
                    LogBus.log("能量雨时长设为 " + v + " 秒");
                });

        addChipRow(card, "点击行",
                new String[]{"偏高", "中上", "中间"},
                new String[]{"0.24", "0.28", "0.34"},
                String.valueOf(RainConfig.rowRatio),
                v -> {
                    RainConfig.rowRatio = Float.parseFloat(v);
                    RainConfig.save(this);
                    LogBus.log("能量雨点击行设为屏幕高度 " + Math.round(RainConfig.rowRatio * 100) + "%");
                });

        addChipRow(card, "频率",
                new String[]{"标准", "快速", "多指"},
                new String[]{"normal", "fast", "multi"},
                RainConfig.speed,
                v -> {
                    RainConfig.speed = v;
                    RainConfig.save(this);
                    LogBus.log("能量雨频率设为 " + RainConfig.speedName()
                            + "（按压 " + RainConfig.tapDuration() + "ms / 间隔 " + RainConfig.tapGap() + "ms）");
                });

        TextView note = new TextView(this);
        note.setText("实测点击速度：标准 27 次/秒，快速 70 次/秒，多指约 250 次/秒（少数页面可能只认第一根手指）。\n"
                + "间隔必须 ≥ 按压时长，否则新手势会打断上一个，表现为「取消」。");
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        note.setTextColor(0xFF9CA3AF);
        note.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.topMargin = dp(14);
        content.addView(note, nlp);

        // ---- 工具 ----
        TextView toolsTitle = new TextView(this);
        toolsTitle.setText("调试工具");
        toolsTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        toolsTitle.setTextColor(0xFF6B7280);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = dp(22);
        tlp.bottomMargin = dp(6);
        content.addView(toolsTitle, tlp);

        addTool(content, "测试识别当前屏幕", v -> {
            ForestAccessibilityService s = ForestAccessibilityService.INSTANCE;
            if (s == null) {
                toast("无障碍服务未连接，请先开启");
                return;
            }
            toast("正在识别当前屏幕…");
            s.probe();
        });
        addTool(content, "预览能量雨悬浮窗（不点击屏幕）", v -> {
            ForestAccessibilityService s = ForestAccessibilityService.INSTANCE;
            if (s == null) {
                toast("无障碍服务未连接，请先开启");
                return;
            }
            s.previewRainHud();
            toast("悬浮窗已弹出");
        });
        addTool(content, "点击测速（保持 App 在前台，约 8 秒）", v -> {
            ForestAccessibilityService s = ForestAccessibilityService.INSTANCE;
            if (s == null) {
                toast("无障碍服务未连接，请先开启");
                return;
            }
            toast("正在测速，结果看首页日志");
            s.tapBenchmark();
        });

        return sv;
    }

    private void addChipRow(LinearLayout parent, String title, String[] labels, String[] values,
                            String selected, ChipPick pick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(11), 0, dp(11));

        TextView name = new TextView(this);
        name.setText(title);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setTextColor(0xFF6B7280);
        row.addView(name, new LinearLayout.LayoutParams(dp(62), -2));

        final TextView[] chips = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView chip = new TextView(this);
            chip.setText(labels[i]);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(13), dp(7), dp(13), dp(7));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                for (int k = 0; k < chips.length; k++) styleChip(chips[k], k == idx);
                pick.onPick(values[idx]);
            });
            chips[i] = chip;
            row.addView(chip);
        }
        for (int i = 0; i < chips.length; i++) styleChip(chips[i], values[i].equals(selected));
        parent.addView(row);
    }

    private void styleChip(TextView chip, boolean on) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(14));
        d.setColor(on ? 0xFF1FA85C : 0xFFF1F5F9);
        chip.setBackground(d);
        chip.setTextColor(on ? Color.WHITE : 0xFF374151);
    }

    private void addTool(LinearLayout parent, String text, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTextColor(0xFF2563EB);
        t.setPadding(dp(4), dp(12), dp(4), dp(12));
        t.setOnClickListener(l);
        parent.addView(t);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
