package com.activeandroid;

import android.content.Context;
import com.activeandroid.serializer.TypeSerializer;
import com.activeandroid.util.Log;
import com.activeandroid.util.ReflectionUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Configuration {
    public static final String SQL_PARSER_DELIMITED = "delimited";
    public static final String SQL_PARSER_LEGACY = "legacy";
    private int mCacheSize;
    private Context mContext;
    private String mDatabaseName;
    private int mDatabaseVersion;
    private List<Class<? extends Model>> mModelClasses;
    private String mSqlParser;
    private List<Class<? extends TypeSerializer>> mTypeSerializers;

    public static class Builder {
        private static final String AA_DB_NAME = "AA_DB_NAME";
        private static final String AA_DB_VERSION = "AA_DB_VERSION";
        private static final String AA_MODELS = "AA_MODELS";
        private static final String AA_SERIALIZERS = "AA_SERIALIZERS";
        private static final String AA_SQL_PARSER = "AA_SQL_PARSER";
        private static final int DEFAULT_CACHE_SIZE = 1024;
        private static final String DEFAULT_DB_NAME = "Application.db";
        private static final String DEFAULT_SQL_PARSER = "legacy";
        private Integer mCacheSize = Integer.valueOf(1024);
        private Context mContext;
        private String mDatabaseName;
        private Integer mDatabaseVersion;
        private List<Class<? extends Model>> mModelClasses;
        private String mSqlParser;
        private List<Class<? extends TypeSerializer>> mTypeSerializers;

        public Builder(Context context) {
            this.mContext = context.getApplicationContext();
        }

        public Builder setCacheSize(int cacheSize) {
            this.mCacheSize = Integer.valueOf(cacheSize);
            return this;
        }

        public Builder setDatabaseName(String databaseName) {
            this.mDatabaseName = databaseName;
            return this;
        }

        public Builder setDatabaseVersion(int databaseVersion) {
            this.mDatabaseVersion = Integer.valueOf(databaseVersion);
            return this;
        }

        public Builder setSqlParser(String sqlParser) {
            this.mSqlParser = sqlParser;
            return this;
        }

        public Builder addModelClass(Class<? extends Model> modelClass) {
            if (this.mModelClasses == null) {
                this.mModelClasses = new ArrayList();
            }
            this.mModelClasses.add(modelClass);
            return this;
        }

        public Builder addModelClasses(Class<? extends Model>... modelClasses) {
            if (this.mModelClasses == null) {
                this.mModelClasses = new ArrayList();
            }
            this.mModelClasses.addAll(Arrays.asList(modelClasses));
            return this;
        }

        public Builder setModelClasses(Class<? extends Model>... modelClasses) {
            this.mModelClasses = Arrays.asList(modelClasses);
            return this;
        }

        public Builder addTypeSerializer(Class<? extends TypeSerializer> typeSerializer) {
            if (this.mTypeSerializers == null) {
                this.mTypeSerializers = new ArrayList();
            }
            this.mTypeSerializers.add(typeSerializer);
            return this;
        }

        public Builder addTypeSerializers(Class<? extends TypeSerializer>... typeSerializers) {
            if (this.mTypeSerializers == null) {
                this.mTypeSerializers = new ArrayList();
            }
            this.mTypeSerializers.addAll(Arrays.asList(typeSerializers));
            return this;
        }

        public Builder setTypeSerializers(Class<? extends TypeSerializer>... typeSerializers) {
            this.mTypeSerializers = Arrays.asList(typeSerializers);
            return this;
        }

        public Configuration create() {
            Configuration configuration = new Configuration(this.mContext);
            configuration.mCacheSize = this.mCacheSize.intValue();
            if (this.mDatabaseName != null) {
                configuration.mDatabaseName = this.mDatabaseName;
            } else {
                configuration.mDatabaseName = getMetaDataDatabaseNameOrDefault();
            }
            if (this.mDatabaseVersion != null) {
                configuration.mDatabaseVersion = this.mDatabaseVersion.intValue();
            } else {
                configuration.mDatabaseVersion = getMetaDataDatabaseVersionOrDefault();
            }
            if (this.mSqlParser != null) {
                configuration.mSqlParser = this.mSqlParser;
            } else {
                configuration.mSqlParser = getMetaDataSqlParserOrDefault();
            }
            if (this.mModelClasses != null) {
                configuration.mModelClasses = this.mModelClasses;
            } else {
                String modelList = (String) ReflectionUtils.getMetaData(this.mContext, AA_MODELS);
                if (modelList != null) {
                    configuration.mModelClasses = loadModelList(modelList.split(","));
                }
            }
            if (this.mTypeSerializers != null) {
                configuration.mTypeSerializers = this.mTypeSerializers;
            } else {
                String serializerList = (String) ReflectionUtils.getMetaData(this.mContext, AA_SERIALIZERS);
                if (serializerList != null) {
                    configuration.mTypeSerializers = loadSerializerList(serializerList.split(","));
                }
            }
            return configuration;
        }

        private String getMetaDataDatabaseNameOrDefault() {
            String aaName = (String) ReflectionUtils.getMetaData(this.mContext, AA_DB_NAME);
            if (aaName == null) {
                return DEFAULT_DB_NAME;
            }
            return aaName;
        }

        private int getMetaDataDatabaseVersionOrDefault() {
            Integer aaVersion = (Integer) ReflectionUtils.getMetaData(this.mContext, AA_DB_VERSION);
            if (aaVersion == null || aaVersion.intValue() == 0) {
                aaVersion = Integer.valueOf(1);
            }
            return aaVersion.intValue();
        }

        private String getMetaDataSqlParserOrDefault() {
            String mode = (String) ReflectionUtils.getMetaData(this.mContext, AA_SQL_PARSER);
            if (mode == null) {
                return "legacy";
            }
            return mode;
        }

        private List<Class<? extends Model>> loadModelList(String[] models) {
            List<Class<? extends Model>> modelClasses = new ArrayList();
            ClassLoader classLoader = this.mContext.getClass().getClassLoader();
            for (String model : models) {
                try {
                    Class modelClass = Class.forName(model.trim(), false, classLoader);
                    if (ReflectionUtils.isModel(modelClass)) {
                        modelClasses.add(modelClass);
                    }
                } catch (Throwable e) {
                    Log.e("Couldn't create class.", e);
                }
            }
            return modelClasses;
        }

        private List<Class<? extends TypeSerializer>> loadSerializerList(String[] serializers) {
            List<Class<? extends TypeSerializer>> typeSerializers = new ArrayList();
            ClassLoader classLoader = this.mContext.getClass().getClassLoader();
            for (String serializer : serializers) {
                try {
                    Class serializerClass = Class.forName(serializer.trim(), false, classLoader);
                    if (ReflectionUtils.isTypeSerializer(serializerClass)) {
                        typeSerializers.add(serializerClass);
                    }
                } catch (Throwable e) {
                    Log.e("Couldn't create class.", e);
                }
            }
            return typeSerializers;
        }
    }

    private Configuration(Context context) {
        this.mContext = context;
    }

    public Context getContext() {
        return this.mContext;
    }

    public String getDatabaseName() {
        return this.mDatabaseName;
    }

    public int getDatabaseVersion() {
        return this.mDatabaseVersion;
    }

    public String getSqlParser() {
        return this.mSqlParser;
    }

    public List<Class<? extends Model>> getModelClasses() {
        return this.mModelClasses;
    }

    public List<Class<? extends TypeSerializer>> getTypeSerializers() {
        return this.mTypeSerializers;
    }

    public int getCacheSize() {
        return this.mCacheSize;
    }

    public boolean isValid() {
        return this.mModelClasses != null && this.mModelClasses.size() > 0;
    }
}
