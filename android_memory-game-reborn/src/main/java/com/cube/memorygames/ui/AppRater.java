package com.cube.memorygames.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import com.cube.memorygames.MemoryApplicationModel;
import com.memory.brain.training.games.R;

public class AppRater {
    private static final Long DAYS_UNTIL_PROMPT = Long.valueOf(1);
    private static final Long DAY_MILLIS = Long.valueOf(86400000);
    private static final Long LAUNCHES_UNTIL_PROMPT_OLD = Long.valueOf(3);
    private static Editor editor;
    private static SharedPreferences prefs;

    private static class LeaveFeedbackCancelListener implements OnClickListener {
        private Context context;
        private String dialogType;

        LeaveFeedbackCancelListener(Context context, String dialogType) {
            this.context = context;
            this.dialogType = dialogType;
        }

        public void onClick(View v) {
            if (AppRater.editor != null) {
                AppRater.editor.putLong("launch_count", 0);
                AppRater.editor.putLong("date_firstlaunch", System.currentTimeMillis());
                AppRater.editor.commit();
            }
        }
    }

    private static class LeaveFeedbackYesListener implements OnClickListener {
        private Context context;
        private String dialogType;

        LeaveFeedbackYesListener(Context context, String dialogType) {
            this.context = context;
            this.dialogType = dialogType;
        }

        public void onClick(View v) {
            if (AppRater.editor != null) {
                AppRater.editor.putBoolean("dontshowagain", true);
                AppRater.editor.commit();
            }
            String uriText = "mailto:apps@pixign.com?subject=" + Uri.encode(this.context.getString(R.string.feedback_subject_format, this.context.getString(R.string.app_name)));
            Intent intent = new Intent("android.intent.action.SENDTO");
            intent.setData(Uri.parse(uriText));
            this.context.startActivity(Intent.createChooser(intent, this.context.getString(R.string.send_feedback)));
        }
    }

    private static class LikeUsNoListener implements OnClickListener {
        private Context context;
        private String dialogType;

        LikeUsNoListener(Context context, String dialogType) {
            this.context = context;
            this.dialogType = dialogType;
        }

        public void onClick(View v) {
            new RateDialog(this.context, R.string.dialog_feedback_title, R.string.dialog_leave_feedback_yes, R.string.dialog_leave_feedback_no, false, R.drawable.lionkid3, new LeaveFeedbackYesListener(this.context, this.dialogType), new LeaveFeedbackCancelListener(this.context, this.dialogType)).show();
        }
    }

    private static class RateUsYesListener implements OnClickListener {
        private Context context;
        private String dialogType;

        RateUsYesListener(Context context, String dialogType) {
            this.context = context;
            this.dialogType = dialogType;
        }

        public void onClick(View v) {
            if (AppRater.editor != null) {
                AppRater.editor.putBoolean("dontshowagain", true);
                AppRater.editor.commit();
            }
            this.context.startActivity(new Intent("android.intent.action.VIEW", Uri.parse("market://details?id=" + this.context.getPackageName())));
        }
    }

    public static void app_launched(Context mContext, boolean fromStart) {
        prefs = mContext.getSharedPreferences("apprater", 0);
        editor = prefs.edit();
        Long date_firstLaunch = Long.valueOf(prefs.getLong("date_firstlaunch", 0));
        if (date_firstLaunch.longValue() == 0) {
            date_firstLaunch = Long.valueOf(System.currentTimeMillis());
            editor.putLong("date_firstlaunch", date_firstLaunch.longValue());
        }
        Long finalDate_firstLaunch = date_firstLaunch;
        if (!prefs.getBoolean("dontshowagain", false)) {
            long launch_count = prefs.getLong("launch_count", 0);
            if (fromStart) {
                launch_count++;
            }
            editor.putLong("launch_count", launch_count);
            if (launch_count >= LAUNCHES_UNTIL_PROMPT_OLD.longValue() && System.currentTimeMillis() >= finalDate_firstLaunch.longValue() + (DAYS_UNTIL_PROMPT.longValue() * DAY_MILLIS.longValue())) {
                showRateDialog(mContext, " Old");
            }
        }
        editor.apply();
    }

    /** The before-exit cross-app promo was removed; back press always exits. */
    public static boolean checkGame248Offer(Activity activity) {
        return true;
    }

    private static void showRateDialog(Context context, String dialogType) {
        RateDialog likeUsDialog = new RateDialog(context, R.string.dialog_new_like_title, R.string.dialog_new_like_us_yes, R.string.dialog_new_like_us_no, true, R.drawable.lionkid2, new RateUsYesListener(context, dialogType), new LikeUsNoListener(context, dialogType));
        if (!((Activity) context).isFinishing()) {
            likeUsDialog.show();
        }
    }
}
