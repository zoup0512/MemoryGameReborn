package com.cube.memorygames.diagnostics;

import android.app.Activity;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.cube.memorygames.api.online.DiagnosticUploader;
import com.cube.memorygames.ui.DialogUi;
import com.memory.brain.training.games.R;
import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.Date;

/** No long-lived Activity capture: background callbacks contain only a weak reference. */
public final class DiagnosticUploadUi {
    private DiagnosticUploadUi() { }
    private static Activity alive(WeakReference<Activity> reference) {
        Activity a=reference.get();
        return a==null || a.isFinishing() || a.isDestroyed() ? null : a;
    }
    public static void show(Activity activity) {
        Toast.makeText(activity,R.string.diagnostics_preparing,Toast.LENGTH_SHORT).show();
        final WeakReference<Activity> ref=new WeakReference<>(activity);
        DiagnosticUploader.prepare(activity.getApplicationContext(),new DiagnosticUploader.Preparation() {
            public void failed(String message) {
                Activity a=alive(ref);
                if(a!=null) Toast.makeText(a,message,Toast.LENGTH_LONG).show();
            }
            public void ready(DiagnosticUploader.Prepared value) {
                Activity a=alive(ref);
                if(a==null){DiagnosticUploader.cancel(value);return;}
                DiagnosticLogStore.Snapshot s=value.snapshot;
                DateFormat dates=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.MEDIUM);
                LinearLayout content=content(a);
                section(a,content,R.string.dialog_diagnostics_range_title,
                        a.getString(R.string.dialog_diagnostics_range,
                                dates.format(new Date(s.startedAt)),dates.format(new Date(s.endedAt)),s.bytes,s.records));
                section(a,content,R.string.dialog_diagnostics_privacy_title,
                        a.getString(R.string.dialog_diagnostics_privacy));
                TextView destination=section(a,content,R.string.dialog_diagnostics_destination_title,
                        value.destination+DiagnosticUploadPolicy.PATH);
                destination.setTextIsSelectable(true);
                section(a,content,R.string.dialog_diagnostics_control_title,
                        a.getString(R.string.dialog_diagnostics_control));
                ScrollView scroll=new ScrollView(a);
                scroll.addView(content);
                AlertDialog dialog=DialogUi.builder(a).setTitle(R.string.diagnostics_upload)
                        .setView(scroll).setNegativeButton(R.string.cancel,null)
                        .setPositiveButton(R.string.diagnostics_send,(d,w) -> {
                            Activity page=alive(ref);
                            if(page==null){DiagnosticUploader.cancel(value);return;}
                            // This explicit action is the only upload entry point.
                            DiagnosticUploader.upload(page.getApplicationContext(),value,new DiagnosticUploader.Result() {
                                public void complete(String id) {
                                    Activity p=alive(ref);
                                    if(p!=null) showSuccess(p,id);
                                }
                                public void failed(String failure) {
                                    Activity p=alive(ref);
                                    if(p!=null)Toast.makeText(p,failure,Toast.LENGTH_LONG).show();
                                }
                            });
                        }).create();
                // cancel() is consumed-aware: dismissing after Send cannot remove an in-flight snapshot.
                dialog.setOnDismissListener(d -> DiagnosticUploader.cancel(value));
                dialog.show();
            }
        });
    }
    private static void showSuccess(Activity activity,String id) {
        LinearLayout content=content(activity);
        TextView reference=section(activity,content,R.string.dialog_diagnostics_id_label,id);
        reference.setTextIsSelectable(true);
        ScrollView scroll=new ScrollView(activity); scroll.addView(content);
        AlertDialog dialog=DialogUi.builder(activity).setTitle(R.string.diagnostics_success)
                .setView(scroll).setNegativeButton(R.string.ok,null)
                .setPositiveButton(R.string.dialog_diagnostics_copy_id,null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> DialogUi.copy(activity,
                activity.getString(R.string.dialog_diagnostics_id_label),id));
    }
    private static LinearLayout content(Activity activity) {
        LinearLayout content=new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(ContextCompat.getColor(activity,R.color.dialog_surface));
        int padding=dp(activity,24);
        content.setPadding(padding,dp(activity,8),padding,padding);
        return content;
    }
    private static TextView section(Activity activity,LinearLayout content,int title,String value) {
        TextView heading=new TextView(activity);
        heading.setText(title); heading.setTextSize(16);
        heading.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        heading.setTextColor(ContextCompat.getColor(activity,R.color.dialog_text));
        heading.setPadding(0,dp(activity,16),0,dp(activity,6));
        content.addView(heading,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView text=new TextView(activity);
        text.setText(value); text.setTextSize(15);
        text.setTextColor(ContextCompat.getColor(activity,R.color.dialog_text_secondary));
        text.setLineSpacing(0,1.15f);
        content.addView(text,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        return text;
    }
    private static int dp(Activity activity,int value) {
        return (int)(value*activity.getResources().getDisplayMetrics().density+0.5f);
    }
}
