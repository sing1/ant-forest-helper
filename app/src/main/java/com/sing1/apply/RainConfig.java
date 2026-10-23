package com.sing1.apply;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 能量雨参数。
 *
 * 频率档位来自实测：
 *   标准 30ms 按压 / 35ms 间隔 → 27 次/秒
 *   快速 10ms 按压 / 12ms 间隔 → 70 次/秒（0 取消，推荐）
 *   多指 一次手势 8 个点同时按 / 30ms 一组 → 约 250 次/秒（游戏可能只认第一根手指）
 */
final class RainConfig {

    static final String PREF = "rain_cfg";

    static int seconds = 15;
    static float rowRatio = 0.28f;
    static String speed = "fast"; // normal | fast | multi

    private RainConfig() {
    }

    static void load(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        seconds = sp.getInt("seconds", 15);
        rowRatio = sp.getFloat("row", 0.28f);
        speed = sp.getString("speed", "fast");
    }

    static void save(Context c) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putInt("seconds", seconds)
                .putFloat("row", rowRatio)
                .putString("speed", speed)
                .apply();
    }

    static boolean multi() {
        return "multi".equals(speed);
    }

    /** 单次点击的按压时长(ms) */
    static int tapDuration() {
        switch (speed) {
            case "multi":
                return 25;
            case "fast":
                return 10;
            default:
                return 30;
        }
    }

    /** 两次点击（或两组多指）之间的间隔(ms) */
    static int tapGap() {
        switch (speed) {
            case "multi":
                return 30;
            case "fast":
                return 12;
            default:
                return 35;
        }
    }

    static String speedName() {
        switch (speed) {
            case "multi":
                return "多指";
            case "fast":
                return "快速";
            default:
                return "标准";
        }
    }
}
