package com.activeandroid;

import android.content.Context;
import com.activeandroid.serializer.CalendarSerializer;
import com.activeandroid.serializer.FileSerializer;
import com.activeandroid.serializer.SqlDateSerializer;
import com.activeandroid.serializer.TypeSerializer;
import com.activeandroid.serializer.UtilDateSerializer;
import com.activeandroid.util.Log;
import com.activeandroid.util.ReflectionUtils;
import com.yalantis.ucrop.util.FileUtils;
import dalvik.system.DexFile;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ModelInfo {
    private Map<Class<? extends Model>, TableInfo> mTableInfos = new HashMap();
    private Map<Class<?>, TypeSerializer> mTypeSerializers = new HashMap<Class<?>, TypeSerializer>() {
        {
            put(Calendar.class, new CalendarSerializer());
            put(Date.class, new SqlDateSerializer());
            put(java.util.Date.class, new UtilDateSerializer());
            put(File.class, new FileSerializer());
        }
    };

    public ModelInfo(Configuration configuration) {
        if (!loadModelFromMetaData(configuration)) {
            try {
                scanForModel(configuration.getContext());
            } catch (Throwable e) {
                Log.e("Couldn't open source path.", e);
            }
        }
        Log.i("ModelInfo loaded.");
    }

    public Collection<TableInfo> getTableInfos() {
        return this.mTableInfos.values();
    }

    public TableInfo getTableInfo(Class<? extends Model> type) {
        return (TableInfo) this.mTableInfos.get(type);
    }

    public TypeSerializer getTypeSerializer(Class<?> type) {
        return (TypeSerializer) this.mTypeSerializers.get(type);
    }

    private boolean loadModelFromMetaData(Configuration configuration) {
        if (!configuration.isValid()) {
            return false;
        }
        List<Class<? extends Model>> models = configuration.getModelClasses();
        if (models != null) {
            for (Class<? extends Model> model : models) {
                this.mTableInfos.put(model, new TableInfo(model));
            }
        }
        List<Class<? extends TypeSerializer>> typeSerializers = configuration.getTypeSerializers();
        if (typeSerializers != null) {
            for (Class<? extends TypeSerializer> typeSerializer : typeSerializers) {
                try {
                    TypeSerializer instance = (TypeSerializer) typeSerializer.newInstance();
                    this.mTypeSerializers.put(instance.getDeserializedType(), instance);
                } catch (Throwable e) {
                    Log.e("Couldn't instantiate TypeSerializer.", e);
                }
            }
        }
        return true;
    }

    private void scanForModel(Context context) throws IOException {
        String path;
        String packageName = context.getPackageName();
        String sourcePath = context.getApplicationInfo().sourceDir;
        List<String> paths = new ArrayList();
        if (sourcePath == null || new File(sourcePath).isDirectory()) {
            Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources("");
            while (resources.hasMoreElements()) {
                path = ((URL) resources.nextElement()).getFile();
                if (path.contains("bin") || path.contains("classes")) {
                    paths.add(path);
                }
            }
        } else {
            Enumeration<String> entries = new DexFile(sourcePath).entries();
            while (entries.hasMoreElements()) {
                paths.add(entries.nextElement());
            }
        }
        for (String path2 : paths) {
            scanForModelClasses(new File(path2), packageName, context.getClassLoader());
        }
    }

    private void scanForModelClasses(File path, String packageName, ClassLoader classLoader) {
        int i = 0;
        if (path.isDirectory()) {
            File[] listFiles = path.listFiles();
            int length = listFiles.length;
            while (i < length) {
                scanForModelClasses(listFiles[i], packageName, classLoader);
                i++;
            }
            return;
        }
        String className = path.getName();
        if (!path.getPath().equals(className)) {
            className = path.getPath();
            if (className.endsWith(".class")) {
                className = className.substring(0, className.length() - 6).replace(System.getProperty("file.separator"), FileUtils.HIDDEN_PREFIX);
                int packageNameIndex = className.lastIndexOf(packageName);
                if (packageNameIndex >= 0) {
                    className = className.substring(packageNameIndex);
                } else {
                    return;
                }
            }
            return;
        }
        try {
            Class<?> discoveredClass = Class.forName(className, false, classLoader);
            if (ReflectionUtils.isModel(discoveredClass)) {
                Class<? extends Model> modelClass = (Class<? extends Model>) (Class) discoveredClass;
                this.mTableInfos.put(modelClass, new TableInfo(modelClass));
            } else if (ReflectionUtils.isTypeSerializer(discoveredClass)) {
                TypeSerializer instance = (TypeSerializer) discoveredClass.newInstance();
                this.mTypeSerializers.put(instance.getDeserializedType(), instance);
            }
        } catch (Throwable e) {
            Log.e("Couldn't create class.", e);
        }
    }
}
