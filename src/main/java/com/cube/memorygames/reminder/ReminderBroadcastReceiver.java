package com.cube.memorygames.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.cube.memorygames.NotificationManager;

public class ReminderBroadcastReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        String message = AppReminder.getMessage(context);
        if (message != null) {
            NotificationManager.getInstance().showRemindNotification(context, message);
        }
    }
}
