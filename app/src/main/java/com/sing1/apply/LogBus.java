package com.sing1.apply;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/** 简单的进程内日志总线：服务线程写，界面读。 */
public final class LogBus {

    public interface Listener {
        void onLog(String line);
    }

    private static final String TAG = "AFH";
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<String> HISTORY = new ArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final SimpleDateFormat TS = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private LogBus() {
    }

    public static void add(Listener l) {
        if (!LISTENERS.contains(l)) LISTENERS.add(l);
    }

    public static void remove(Listener l) {
        LISTENERS.remove(l);
    }

    public static void log(String msg) {
        final String line = TS.format(new Date()) + "  " + msg;
        Log.i(TAG, msg);
        synchronized (HISTORY) {
            HISTORY.add(line);
            if (HISTORY.size() > 500) HISTORY.remove(0);
        }
        MAIN.post(() -> {
            for (Listener l : LISTENERS) l.onLog(line);
        });
    }

    public static List<String> history() {
        synchronized (HISTORY) {
            return new ArrayList<>(HISTORY);
        }
    }

    public static void clear() {
        synchronized (HISTORY) {
            HISTORY.clear();
        }
    }
}
