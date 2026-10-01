package com.cube.memorygames.utils;

import android.content.Context;
import android.text.TextUtils;
import com.memory.brain.training.games.R;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class DateUtils {
    public static String getTodayYesterdayOrFormattedDateString(Context context, long time, String format) {
        if (isToday(time)) {
            return context.getString(R.string.text_today);
        }
        if (isYesterday(time)) {
            return context.getString(R.string.text_yesterday);
        }
        if (TextUtils.isEmpty(format)) {
            return android.text.format.DateUtils.formatDateTime(context, time, 20);
        }
        return new SimpleDateFormat(format).format(new Date(time));
    }

    public static boolean isToday(long time) {
        Calendar calendar = Calendar.getInstance();
        int today = (calendar.get(1) * 100) + calendar.get(6);
        calendar.setTimeInMillis(time);
        if (today == (calendar.get(1) * 100) + calendar.get(6)) {
            return true;
        }
        return false;
    }

    public static boolean isYesterday(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(6, -1);
        int yesterday = (calendar.get(1) * 100) + calendar.get(6);
        calendar.setTimeInMillis(time);
        if (yesterday == (calendar.get(1) * 100) + calendar.get(6)) {
            return true;
        }
        return false;
    }

    public static boolean isTomorrow(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(6, 1);
        int tomorrow = (calendar.get(1) * 100) + calendar.get(6);
        calendar.setTimeInMillis(time);
        if (tomorrow == (calendar.get(1) * 100) + calendar.get(6)) {
            return true;
        }
        return false;
    }
}
