package com.cube.memorygames.diagnostics;

import android.content.Context;
import java.io.File;

/** Fail-open facade: diagnostics must not change game control flow. */
public final class DiagnosticLog {
    private static volatile DiagnosticLogStore store;
    private DiagnosticLog() { }
    public static void init(Context context) {
        try {
            if(store!=null) return;
            store=new DiagnosticLogStore(new File(context.getFilesDir(),"diagnostics"));
            event("application_start");
            final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread,error) -> {
                try {
                    event("fatal","reason",error.getClass().getName(),"stack",DiagnosticSanitizer.stack(error.getStackTrace()));
                    DiagnosticLogStore s=store;if(s!=null) s.flush(60L);
                } catch(Throwable ignored) { }
                finally { if(previous!=null) previous.uncaughtException(thread,error); else {android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);} }
            });
        } catch(RuntimeException ignored) { }
    }
    public static DiagnosticLogStore store() { return store; }
    public static void event(String event,Object... fields) {try {DiagnosticLogStore s=store;if(s!=null) s.log(event,fields);}catch(RuntimeException ignored){}}
    public static String callerStack() {try{return DiagnosticSanitizer.stack(new Throwable().getStackTrace());}catch(RuntimeException e){return "";}}
}
