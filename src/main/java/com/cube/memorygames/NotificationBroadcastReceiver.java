package com.cube.memorygames;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NotificationBroadcastReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        NotificationManager.getInstance().handleAction(context, intent.getAction());
    }
}
