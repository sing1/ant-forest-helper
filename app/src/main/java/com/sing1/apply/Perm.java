package com.sing1.apply;

import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

/** 权限状态：无障碍 / 通知 / 悬浮窗 */
final class Perm {

    private Perm() {
    }

    /**
     * 无障碍服务是否已开启。
     * 注意：不要只用 AccessibilityManager.getEnabledAccessibilityServiceList()，
     * 小米/澎湃上它经常返回不带 ResolveInfo 的条目，会导致"服务其实在跑却显示未开启"。
     */
    static boolean accessibility(Context c) {
        if (ForestAccessibilityService.INSTANCE != null) return true;
        try {
            String enabled = Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled != null && enabled.contains(c.getPackageName() + "/")) {
                String on = Settings.Secure.getString(c.getContentResolver(),
                        Settings.Secure.ACCESSIBILITY_ENABLED);
                if ("1".equals(on)) return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            android.view.accessibility.AccessibilityManager am =
                    (android.view.accessibility.AccessibilityManager) c.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am != null) {
                for (android.accessibilityservice.AccessibilityServiceInfo info :
                        am.getEnabledAccessibilityServiceList(
                                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                    if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null
                            && c.getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static boolean notification(Context c) {
        if (android.os.Build.VERSION.SDK_INT < 33) return true;
        try {
            return c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean overlay(Context c) {
        try {
            return Settings.canDrawOverlays(c);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 首页用的简短状态 */
    static String summary(Context c) {
        boolean a = accessibility(c);
        boolean n = notification(c);
        if (a && n) return "已就绪";
        int miss = 0;
        if (!a) miss++;
        if (!n) miss++;
        return miss + " 项未开启";
    }

    static boolean ready(Context c) {
        return accessibility(c) && notification(c);
    }
}
