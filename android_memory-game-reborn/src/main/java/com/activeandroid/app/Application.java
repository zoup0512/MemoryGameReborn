package com.activeandroid.app;

import android.content.Context;
import com.activeandroid.ActiveAndroid;

public class Application extends android.app.Application {
    public void onCreate() {
        super.onCreate();
        ActiveAndroid.initialize((Context) this);
    }

    public void onTerminate() {
        super.onTerminate();
        ActiveAndroid.dispose();
    }
}
