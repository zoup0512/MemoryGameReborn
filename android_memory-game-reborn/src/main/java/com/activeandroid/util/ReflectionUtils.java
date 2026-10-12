package com.activeandroid.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.serializer.TypeSerializer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ReflectionUtils {
    public static boolean isModel(Class<?> type) {
        return isSubclassOf(type, Model.class) && !Modifier.isAbstract(type.getModifiers());
    }

    public static boolean isTypeSerializer(Class<?> type) {
        return isSubclassOf(type, TypeSerializer.class);
    }

    public static <T> T getMetaData(Context context, String name) {
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), 128);
            if (ai.metaData != null) {
                return (T) ai.metaData.get(name);
            }
        } catch (Exception e) {
            Log.w("Couldn't find meta-data: " + name);
        }
        return null;
    }

    public static Set<Field> getDeclaredColumnFields(Class<?> type) {
        Set<Field> declaredColumnFields = Collections.emptySet();
        if (isSubclassOf(type, Model.class) || Model.class.equals(type)) {
            declaredColumnFields = new LinkedHashSet();
            Field[] fields = type.getDeclaredFields();
            Arrays.sort(fields, new Comparator<Field>() {
                public int compare(Field field1, Field field2) {
                    return field2.getName().compareTo(field1.getName());
                }
            });
            for (Field field : fields) {
                if (field.isAnnotationPresent(Column.class)) {
                    declaredColumnFields.add(field);
                }
            }
            Class<?> parentType = type.getSuperclass();
            if (parentType != null) {
                declaredColumnFields.addAll(getDeclaredColumnFields(parentType));
            }
        }
        return declaredColumnFields;
    }

    public static boolean isSubclassOf(Class<?> type, Class<?> superClass) {
        if (type.getSuperclass() == null) {
            return false;
        }
        if (type.getSuperclass().equals(superClass)) {
            return true;
        }
        return isSubclassOf(type.getSuperclass(), superClass);
    }
}
