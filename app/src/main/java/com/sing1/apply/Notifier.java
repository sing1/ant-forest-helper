package com.sing1.apply;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** 进度通知：收取过程中持续显示，可一键停止。 */
public final class Notifier {

    private static final String CHANNEL = "af_run";
    private static final int ID = 1001;

    private Notifier() {
    }

    public static void init(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL, "收取进度", NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
        }
    }

    private static Notification build(Context ctx, String title, String text, boolean ongoing, boolean withStop) {
        Intent open = new Intent(ctx, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent po = PendingIntent.getActivity(ctx, 1, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(ctx, CHANNEL)
                : new Notification.Builder(ctx);
        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
                .setContentIntent(po);

        if (withStop) {
            Intent stop = new Intent(ctx, StopReceiver.class);
            PendingIntent ps = PendingIntent.getBroadcast(ctx, 2, stop,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            b.addAction(new Notification.Action.Builder(null, "停止", ps).build());
        }
        return b.build();
    }

    private static void notify(Context ctx, Notification n) {
        try {
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) nm.notify(ID, n);
        } catch (Throwable t) {
            LogBus.log("通知发送失败: " + t);
        }
    }

    public static void progress(Context ctx, String text) {
        notify(ctx, build(ctx, "正在收取能量", text, true, true));
    }

    public static void finish(Context ctx, String text) {
        notify(ctx, build(ctx, "收取结束", text, false, false));
    }
}
