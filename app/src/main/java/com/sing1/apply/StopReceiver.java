package com.sing1.apply;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StopReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        LogBus.log("收到「停止」指令");
        Runner.requestStop();
    }
}
