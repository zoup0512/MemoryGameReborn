package com.cube.memorygames;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import androidx.core.app.NotificationCompat.Builder;
import android.text.TextUtils;
import com.cube.memorygames.reminder.AppReminder;
import com.memory.brain.training.games.R;
import java.util.HashSet;
import java.util.Set;

public class NotificationManager {
    private static final String INTENT_ACTION_CANCEL = "com.cube.memorygames.ACTION_CANCEL";
    private static final String INTENT_ACTION_PLAY = "com.cube.memorygames.ACTION_PLAY";
    private static final String INTENT_ACTION_PLAY_REMINDER = "com.cube.memorygames.ACTION_PLAY_REMINDER";
    private static final String INTENT_ACTION_VIEW = "com.cube.memorygames.ACTION_VIEW";
    private static final int NOTIFICATION_ID = 12345;
    private static NotificationManager instance = new NotificationManager();
    private Set<OnNotificationListener> listeners = new HashSet();

    public interface OnNotificationListener {
        void onCancelClicked();

        void onPlayClicked();

        void onViewClicked();
    }

    private NotificationManager() {
    }

    public static NotificationManager getInstance() {
        return instance;
    }

    public void handleAction(Context context, String action) {
        if (!TextUtils.isEmpty(action)) {
            int obj = -1;
            switch (action.hashCode()) {
                case -1867555933:
                    if (action.equals(INTENT_ACTION_PLAY)) {
                        obj = 1;
                        break;
                    }
                    break;
                case -1867379948:
                    if (action.equals(INTENT_ACTION_VIEW)) {
                        obj = 3;
                        break;
                    }
                    break;
                case 193108777:
                    if (action.equals(INTENT_ACTION_CANCEL)) {
                        obj = 0;
                        break;
                    }
                    break;
                case 2135682446:
                    if (action.equals(INTENT_ACTION_PLAY_REMINDER)) {
                        obj = 2;
                        break;
                    }
                    break;
            }
            switch (obj) {
                case 0:
                    hideNotifications(context);
                    notifyCancelClicked();
                    return;
                case 1:
                    hideNotifications(context);
                    notifyPlayClicked();
                    startAppIfNeeded(context);
                    return;
                case 2:
                    hideNotifications(context);
                    startAppFromReminder(context);
                    return;
                case 3:
                    notifyViewClicked();
                    startAppIfNeeded(context);
                    return;
                default:
                    return;
            }
        }
    }

    private void startAppIfNeeded(Context context) {
        if (this.listeners.isEmpty()) {
            context.sendBroadcast(new Intent("android.intent.action.CLOSE_SYSTEM_DIALOGS"));
            Intent intent = new Intent(context, MainMenuActivity.class);
            intent.putExtra(MainMenuActivity.EXTRA_ONLINE, true);
            intent.setFlags(268435456);
            context.startActivity(intent);
            hideNotifications(context);
        }
    }

    private void startAppFromReminder(Context context) {
        AppReminder.logReminder(context);
        context.sendBroadcast(new Intent("android.intent.action.CLOSE_SYSTEM_DIALOGS"));
        Intent intent = new Intent(context, MainMenuActivity.class);
        intent.setFlags(268468224);
        context.startActivity(intent);
        hideNotifications(context);
    }

    private void notifyCancelClicked() {
        for (OnNotificationListener listener : this.listeners) {
            listener.onCancelClicked();
        }
    }

    private void notifyPlayClicked() {
        for (OnNotificationListener listener : this.listeners) {
            listener.onPlayClicked();
        }
    }

    private void notifyViewClicked() {
        for (OnNotificationListener listener : this.listeners) {
            listener.onViewClicked();
        }
    }





    public void showRemindNotification(Context context, String message) {
        Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(INTENT_ACTION_PLAY_REMINDER),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        ((android.app.NotificationManager) context.getSystemService("notification")).notify(NOTIFICATION_ID, new Builder(context).setSmallIcon(R.drawable.ic_notification).setContentTitle(context.getString(R.string.app_name)).setLargeIcon(largeIcon).setOngoing(false).setDefaults(1).setContentIntent(pendingIntent).addAction(0, context.getString(R.string.play), pendingIntent).setContentText(message).build());
    }




    public void hideNotifications(Context context) {
        ((android.app.NotificationManager) context.getSystemService("notification")).cancel(NOTIFICATION_ID);
    }
}
