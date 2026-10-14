package com.cube.memorygames.diagnostics;

import android.app.Activity;
import android.support.v7.app.AlertDialog;
import android.widget.Toast;
import com.cube.memorygames.api.online.DiagnosticUploader;
import com.memory.brain.training.games.R;
import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.Date;

/** No long-lived Activity capture: background callbacks contain only a weak reference. */
public final class DiagnosticUploadUi {
    private DiagnosticUploadUi() { }
    private static Activity alive(WeakReference<Activity> reference) {Activity a=reference.get();return a==null || a.isFinishing() || a.isDestroyed() ? null : a;}
    public static void show(Activity activity) {
        Toast.makeText(activity,R.string.diagnostics_preparing,Toast.LENGTH_SHORT).show();
        final WeakReference<Activity> ref=new WeakReference<>(activity);
        DiagnosticUploader.prepare(activity.getApplicationContext(),new DiagnosticUploader.Preparation() {
            public void failed(String message) {Activity a=alive(ref);if(a!=null) Toast.makeText(a,message,Toast.LENGTH_LONG).show();}
            public void ready(DiagnosticUploader.Prepared value) {
                Activity a=alive(ref);if(a==null){DiagnosticUploader.cancel(value);return;}
                DiagnosticLogStore.Snapshot s=value.snapshot;
                DateFormat dates=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.MEDIUM);
                String message=a.getString(R.string.diagnostics_confirmation,
                        dates.format(new Date(s.startedAt)),dates.format(new Date(s.endedAt)),s.bytes,s.records,value.destination+DiagnosticUploadPolicy.PATH);
                AlertDialog dialog=new AlertDialog.Builder(a).setTitle(R.string.diagnostics_upload)
                        .setMessage(message).setNegativeButton(R.string.cancel,(d,w) -> DiagnosticUploader.cancel(value))
                        .setPositiveButton(R.string.diagnostics_send,(d,w) -> {
                            Activity page=alive(ref);if(page==null){DiagnosticUploader.cancel(value);return;}
                            DiagnosticUploader.upload(page.getApplicationContext(),value,new DiagnosticUploader.Result() {
                                public void complete(String id) {Activity p=alive(ref);if(p!=null)new AlertDialog.Builder(p).setTitle(R.string.diagnostics_success).setMessage(id).setPositiveButton(R.string.ok,null).show();}
                                public void failed(String failure) {Activity p=alive(ref);if(p!=null)Toast.makeText(p,failure,Toast.LENGTH_LONG).show();}
                            });
                        }).create();
                dialog.setOnDismissListener(d -> DiagnosticUploader.cancel(value));
                dialog.show();
            }
        });
    }
}
