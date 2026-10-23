package com.sing1.apply;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 权限页：已授权的只显示状态，不再显示按钮 */
public class PermissionsActivity extends Activity {

    private Row accRow;
    private Row notifRow;
    private Row overlayRow;

    private static final class Row {
        LinearLayout view;
        TextView value;
        TextView action;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(build());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private View build() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(0xFFF2F4F7);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        sv.addView(root, new ViewGroup.LayoutParams(-1, -2));

        // 顶栏
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
        title.setText("权限");
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

        accRow = addRow(card, "无障碍服务", "去开启", v -> {
            toast("在列表里找到「森林助手」并打开");
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception e) {
                toast("无法打开无障碍设置：" + e.getMessage());
            }
        });
        notifRow = addRow(card, "通知权限", "去授权", v -> {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        });
        overlayRow = addRow(card, "悬浮窗（备用）", "去授权", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                toast("无法打开悬浮窗设置：" + e.getMessage());
            }
        });

        TextView note = new TextView(this);
        note.setText("• 无障碍服务只开一次，之后重启手机通常也还在；\n"
                + "• 如果发现它被系统自动关掉，去「应用设置 → 森林助手」允许「自启动」并设为「省电无限制」；\n"
                + "• 点「关闭」没能回到 App 时，允许小米的「后台弹出界面」权限；\n"
                + "• 悬浮窗一般是可选，能量雨小窗走的是无障碍浮层，不需要这项权限。");
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        note.setTextColor(0xFF6B7280);
        note.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.topMargin = dp(18);
        content.addView(note, nlp);

        return sv;
    }

    private Row addRow(LinearLayout parent, String label, String actionText, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(15), 0, dp(15));

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        name.setTextColor(0xFF111827);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

        Row r = new Row();
        r.view = row;

        r.value = new TextView(this);
        r.value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        r.value.setTextColor(0xFF9CA3AF);
        row.addView(r.value);

        r.action = new TextView(this);
        r.action.setText(actionText);
        r.action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        r.action.setTextColor(0xFF1FA85C);
        r.action.setPadding(dp(14), dp(4), dp(2), dp(4));
        r.action.setOnClickListener(click);
        row.addView(r.action);

        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));

        View line = new View(this);
        line.setBackgroundColor(0xFFF3F4F6);
        parent.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.7f))));
        return r;
    }

    private void refresh() {
        boolean a = Perm.accessibility(this);
        accRow.value.setText(a ? "已开启" : "未开启");
        accRow.value.setTextColor(a ? 0xFF16A34A : 0xFFDC2626);
        accRow.action.setVisibility(a ? View.GONE : View.VISIBLE);

        boolean n = Perm.notification(this);
        notifRow.value.setText(n ? "已允许" : "未允许");
        notifRow.value.setTextColor(n ? 0xFF16A34A : 0xFFDC2626);
        notifRow.action.setVisibility(n ? View.GONE : View.VISIBLE);

        boolean o = Perm.overlay(this);
        overlayRow.value.setText(o ? "已允许" : "可选");
        overlayRow.value.setTextColor(o ? 0xFF16A34A : 0xFF9CA3AF);
        overlayRow.action.setVisibility(o ? View.GONE : View.VISIBLE);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
