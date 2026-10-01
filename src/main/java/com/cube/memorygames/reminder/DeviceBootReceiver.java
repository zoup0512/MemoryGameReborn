package com.cube.memorygames.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class DeviceBootReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            AppReminder.setAlarm(context);
        }
    }
}
