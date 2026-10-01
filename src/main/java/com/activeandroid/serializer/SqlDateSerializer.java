package com.activeandroid.serializer;

import java.sql.Date;

public final class SqlDateSerializer extends TypeSerializer {
    public Class<?> getDeserializedType() {
        return Date.class;
    }

    public Class<?> getSerializedType() {
        return Long.TYPE;
    }

    public Long serialize(Object data) {
        if (data == null) {
            return null;
        }
        return Long.valueOf(((Date) data).getTime());
    }

    public Date deserialize(Object data) {
        if (data == null) {
            return null;
        }
        return new Date(((Long) data).longValue());
    }
}
