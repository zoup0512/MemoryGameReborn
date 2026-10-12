package com.activeandroid.serializer;

import java.util.Calendar;

public final class CalendarSerializer extends TypeSerializer {
    public Class<?> getDeserializedType() {
        return Calendar.class;
    }

    public Class<?> getSerializedType() {
        return Long.TYPE;
    }

    public Long serialize(Object data) {
        return Long.valueOf(((Calendar) data).getTimeInMillis());
    }

    public Calendar deserialize(Object data) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(((Long) data).longValue());
        return calendar;
    }
}
