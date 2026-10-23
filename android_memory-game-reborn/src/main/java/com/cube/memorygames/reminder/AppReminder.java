package com.cube.memorygames.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import com.cube.memorygames.MemoryApplicationModel;
import com.memory.brain.training.games.R;
import java.util.GregorianCalendar;

public class AppReminder {
    private static final String PREF_LAST_GAME_TIME = "lastGameTime";
    private static final String PREF_REMINDER_SHOWED_TIME = "reminderShowedTime";
    private static final String PREF_REMINDER_SHOWED_TYPE = "reminderShowedType";
    private static Long lastGameTime;
    private static Long reminderShowedTime;
    private static Integer reminderShowedType;

    public static void appStarted(Context context) {
        setLastGameTime(context, System.currentTimeMillis());
        setAlarm(context);
    }

    public static void logReminder(Context context) {
        if (getReminderShowedTime(context).longValue() != 0) {
            setReminderShowedTime(context, 0);
            setReminderShowedTime(context, 0);
        }
    }

    public static void setAlarm(Context context) {
        Intent alarmIntent = new Intent(context, ReminderBroadcastReceiver.class);
        if (PendingIntent.getBroadcast(context, 0, alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE) == null) {
            ((AlarmManager) context.getSystemService("alarm")).setInexactRepeating(0, System.currentTimeMillis(), 86400000,
                    PendingIntent.getBroadcast(context, 0, alarmIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            context.getPackageManager().setComponentEnabledSetting(new ComponentName(context, DeviceBootReceiver.class), 1, 1);
        }
    }

    public static String getMessage(Context context) {
        int messageType;
        String message;
        long difference = System.currentTimeMillis() - getLastGameTime(context).longValue();
        if (difference >= 604800000) {
            messageType = 3;
            message = " 💪🕶 " + context.getString(R.string.message_type_3);
        } else if (difference >= 259200000) {
            messageType = 2;
            message = " 😎👌 " + context.getString(R.string.message_type_2);
        } else if (difference < 86400000) {
            messageType = 0;
            message = null;
        } else if (new GregorianCalendar().get(7) == 6) {
            messageType = 4;
            message = " 🎮👑😉 " + context.getString(R.string.message_type_4);
        } else {
            messageType = 1;
            message = " 🏆🏅😎 " + context.getString(R.string.message_type_1);
        }
        setReminderShowedTime(context, System.currentTimeMillis());
        setReminderShowedType(context, messageType);
        return message;
    }

    private static Long getLastGameTime(Context context) {
        if (lastGameTime == null) {
            lastGameTime = Long.valueOf(PreferenceManager.getDefaultSharedPreferences(context).getLong(PREF_LAST_GAME_TIME, 0));
        }
        return lastGameTime;
    }

    private static void setLastGameTime(Context context, long lastGameTime) {
        lastGameTime = Long.valueOf(lastGameTime);
        PreferenceManager.getDefaultSharedPreferences(context).edit().putLong(PREF_LAST_GAME_TIME, lastGameTime).apply();
    }

    private static Long getReminderShowedTime(Context context) {
        if (reminderShowedTime == null) {
            reminderShowedTime = Long.valueOf(PreferenceManager.getDefaultSharedPreferences(context).getLong(PREF_REMINDER_SHOWED_TIME, 0));
        }
        return reminderShowedTime;
    }

    private static void setReminderShowedTime(Context context, long reminderShowedTime) {
        reminderShowedTime = Long.valueOf(reminderShowedTime);
        PreferenceManager.getDefaultSharedPreferences(context).edit().putLong(PREF_REMINDER_SHOWED_TIME, reminderShowedTime).apply();
    }

    private static void setReminderShowedType(Context context, int reminderShowedType) {
        reminderShowedType = Integer.valueOf(reminderShowedType);
        PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(PREF_REMINDER_SHOWED_TYPE, reminderShowedType).apply();
    }
}
