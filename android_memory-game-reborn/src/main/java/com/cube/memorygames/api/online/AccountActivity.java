package com.cube.memorygames.api.online;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.util.Patterns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.memory.brain.training.games.BuildConfig;
import com.memory.brain.training.games.R;

/** Programmatic support-26 account forms, matching OnlineUi cards; no database migrations. */
public final class AccountActivity extends AppCompatActivity {
    private AccountManager manager;
    private LinearLayout page;
    private TextView status;
    private boolean loading;
    private AccountManager.UserProfile user;
    private AlertDialog form;
    private TextView formError;
    private TextView formSubmit;
    private TextView formSend;
    private interface FormAction { void run(EditText[] fields); }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        OnlineSession.get().init(this);
        manager=AccountManager.get(this);
        user=manager.profile();
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(OnlineUi.color(this,R.color.online_page));
        root.addView(OnlineUi.toolbar(this,R.string.online_account_title,v -> finish()).view);
        ScrollView scroll=new ScrollView(this);
        page=OnlineUi.page(this); scroll.addView(page);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
        render(); refresh();
    }
    @Override public void onBackPressed() {
        if (!loading) super.onBackPressed();
    }
    private boolean active() { return !isFinishing() && !isDestroyed(); }
    private TextView text(String value) {
        TextView view=OnlineUi.text(this,value,14,R.color.online_text_secondary,OnlineUi.regular(this));
        view.setPadding(0,OnlineUi.dp(this,8),0,OnlineUi.dp(this,8)); return view;
    }
    private void action(LinearLayout parent,int label,Runnable runnable) {
        TextView button=OnlineUi.secondaryButton(this,label);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,OnlineUi.dp(this,52)); params.topMargin=OnlineUi.dp(this,8);
        parent.addView(button,params);
        button.setEnabled(!loading);
        button.setOnClickListener(v -> {
            if (loading) return;
            if (!OnlineSession.get().canChangeAccount()) { status.setText(R.string.online_account_busy); return; }
            runnable.run();
        });
    }
    private void render() {
        if (!active()) return;
        page.removeAllViews();
        LinearLayout card=OnlineUi.emphasisCard(this);
        if (user != null) {
            OnlineUi.AvatarBlock avatar=OnlineUi.avatar(this,64,false);
            OnlineUi.applyAvatar(this,avatar,user.avatarValue); card.addView(avatar.view);
            card.addView(text(getString(user.isGuest() ? R.string.account_guest : R.string.account_registered)
                    + " #" + user.userId + "\n" + user.nickname + "\n" + (user.email == null ? "" : user.email)
                    + "\nELO " + user.elo + " · " + getString(R.string.online_account_balance,user.balance)));
        } else {
            OnlineMessages.AuthOk old=OnlineSession.get().getLastProfile();
            card.addView(text(getString(R.string.account_guest) + (old == null ? "" : " #"+old.userId+"\n"+old.nickname)));
        }
        card.addView(text(getString(user == null || user.isGuest() ? R.string.account_warning : R.string.account_registered_note)));
        status=text(loading ? getString(R.string.account_loading) : ""); card.addView(status);
        page.addView(card,OnlineUi.cardParams(this));
        LinearLayout actions=OnlineUi.card(this);
        action(actions,R.string.account_retry,this::refresh);
        if (user == null || user.isGuest()) action(actions,R.string.account_bind,this::bindForm);
        action(actions,R.string.account_login,() -> confirm(R.string.account_login_warning,() -> loginForm(false)));
        if (user != null) {
            action(actions,R.string.account_profile,this::profileForm);
            action(actions,R.string.account_devices,this::devices);
            action(actions,R.string.account_logout,() -> confirm(R.string.account_logout_warning,() -> logout(false)));
            if (!user.isGuest()) {
                action(actions,R.string.account_logout_all,() -> confirm(R.string.account_logout_warning,() -> logout(true)));
                action(actions,R.string.account_delete,() -> confirm(R.string.account_delete_warning,this::deleteForm));
            }
        }
        if (manager.needsLogin()) action(actions,R.string.account_forget,() -> confirm(R.string.account_forget_warning,() -> {
            start(); manager.forgetRevokedIdentity(new AccountManager.Callback<Boolean>() {
                public void onSuccess(Boolean value) { accountChanged(); }
                public void onError(String message) { fail(message); }
            });
        }));
        action(actions,R.string.account_recover_guest,() -> confirm(R.string.account_recover_warning,() -> {
            start(); manager.recoverGuest(authCallback());
        }));
        action(actions,R.string.account_reset,this::resetForm);
        action(actions,R.string.account_cancel_delete,() -> loginForm(true));
        page.addView(actions,OnlineUi.cardParams(this));
        LinearLayout environment=OnlineUi.card(this);
        try { environment.addView(text(OnlineConfig.getEndpoint(this))); }
        catch (RuntimeException e) { environment.addView(text(e.getMessage())); }
        environment.addView(text(getString(R.string.account_environment_note)));
        if (manager.canMigrateLegacy()) action(environment,R.string.account_migrate,this::migrationForm);
        if (BuildConfig.DEBUG) action(environment,R.string.account_environment,this::environmentForm);
        page.addView(environment,OnlineUi.cardParams(this));
    }
    private void refresh() {
        if (loading) return;
        start(); manager.me(new AccountManager.Callback<AccountManager.UserProfile>() {
            public void onSuccess(AccountManager.UserProfile value) { if (!active()) return; user=value; loading=false; render(); }
            public void onError(String message) { fail(message); }
        });
    }
    private void start() {
        loading=true;
        if (formSubmit != null) formSubmit.setEnabled(false);
        if (formSend != null) formSend.setEnabled(false);
        if (form != null) { form.setCancelable(false); if(formError!=null) formError.setText(R.string.account_loading); }
        render();
    }
    private void fail(String message) {
        if (!active()) return;
        loading=false;
        // Initial /me may fail after loading persisted identity; retain its profile and recovery actions.
        user=manager.profile();
        render(); status.setText(message);
        if (formError != null) formError.setText(message);
        if (formSubmit != null) formSubmit.setEnabled(true);
        if (formSend != null) formSend.setEnabled(true);
        if (form != null) form.setCancelable(true);
    }
    private void done() {
        if (!active()) return;
        loading=false; closeForm(); render(); status.setText(R.string.account_success);
    }
    private void closeForm() {
        if (form != null) form.dismiss(); form=null; formError=null; formSubmit=null; formSend=null;
    }
    private void accountChanged() {
        OnlineSession.get().resetForAccountChange(); OnlineSession.get().connect();
        if (!active()) return;
        user=manager.profile(); done(); refresh();
    }
    private AccountManager.Callback<AccountManager.UserProfile> authCallback() {
        return new AccountManager.Callback<AccountManager.UserProfile>() {
            public void onSuccess(AccountManager.UserProfile value) { user=value; accountChanged(); }
            public void onError(String message) { fail(message); }
        };
    }
    private void confirm(int message,Runnable action) {
        new AlertDialog.Builder(this).setTitle(R.string.online_account_title).setMessage(message)
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.account_confirm,(d,w)->action.run()).show();
    }
    private EditText field(LinearLayout container,int hint,int type) {
        EditText field=new EditText(this); field.setHint(hint); field.setInputType(type); field.setSingleLine(true);
        if ((type & InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD) {
            field.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
        }
        field.setSaveEnabled(false); // Passwords/codes are never serialized to savedInstanceState.
        container.addView(field,new LinearLayout.LayoutParams(-1,-2)); return field;
    }
    private EditText[] form(int title,int[] hints,int[] types,FormAction action) {
        closeForm();
        LinearLayout content=OnlineUi.page(this);
        EditText[] fields=new EditText[hints.length];
        for(int i=0;i<hints.length;i++) fields[i]=field(content,hints[i],types[i]);
        formError=text(""); content.addView(formError);
        formSubmit=OnlineUi.primaryButton(this,R.string.account_confirm); content.addView(formSubmit,new LinearLayout.LayoutParams(-1,OnlineUi.dp(this,52)));
        formSubmit.setOnClickListener(v -> {
            if (loading) return;
            for(EditText field:fields) if(field.getText().toString().trim().isEmpty()) { formError.setText(R.string.account_required); return; }
            for(int i=0;i<hints.length;i++) if(hints[i]==R.string.account_email
                    && !Patterns.EMAIL_ADDRESS.matcher(fields[i].getText().toString().trim()).matches()) {
                formError.setText(R.string.account_email_invalid); return;
            }
            action.run(fields);
        });
        ScrollView scroll=new ScrollView(this); scroll.addView(content);
        form=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton(R.string.cancel,null).create();
        form.show(); return fields;
    }
    private String value(EditText field) { return field.getText().toString(); }
    private static final int EMAIL=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
    private static final int PASSWORD=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD;
    private void loginForm(boolean cancelDelete) {
        form(cancelDelete ? R.string.account_cancel_delete : R.string.account_login,
                new int[]{R.string.account_email,R.string.account_password},new int[]{EMAIL,PASSWORD}, fields -> {
                    start(); manager.login(value(fields[0]).trim(),value(fields[1]),cancelDelete,authCallback());
                });
    }
    private void bindForm() {
        EditText[] fields=form(R.string.account_bind,new int[]{R.string.account_email,R.string.account_password,R.string.account_code},
                new int[]{EMAIL,PASSWORD,InputType.TYPE_CLASS_TEXT}, f -> {
                    start(); manager.register(value(f[0]).trim(),value(f[1]),value(f[2]).trim(),authCallback());
                });
        LinearLayout parent=(LinearLayout)fields[0].getParent();
        formSend=OnlineUi.secondaryButton(this,R.string.account_send_code); parent.addView(formSend,new LinearLayout.LayoutParams(-1,OnlineUi.dp(this,52)));
        formSend.setOnClickListener(v -> {
            if (!Patterns.EMAIL_ADDRESS.matcher(value(fields[0]).trim()).matches()) { formError.setText(R.string.account_email_invalid); return; }
            start(); manager.request("POST","/account/email-verification",AccountManager.body("email",value(fields[0]).trim()),true,false,
                    messageCallback(false));
        });
    }
    private AccountManager.Callback<JsonObject> messageCallback(boolean close) {
        return new AccountManager.Callback<JsonObject>() {
            public void onSuccess(JsonObject value) {
                if (!active()) return;
                if (close) done();
                else { loading=false; render(); if(formError!=null)formError.setText(R.string.account_success);
                    if(formSubmit!=null)formSubmit.setEnabled(true); if(formSend!=null)formSend.setEnabled(true); if(form!=null)form.setCancelable(true); }
            }
            public void onError(String message) { fail(message); }
        };
    }
    private void logout(boolean all) {
        start(); manager.logout(all,new AccountManager.Callback<Boolean>() {
            public void onSuccess(Boolean value) { accountChanged(); }
            public void onError(String message) { fail(message); }
        });
    }
    private void resetForm() {
        EditText[] fields=form(R.string.account_reset,new int[]{R.string.account_email,R.string.account_code,R.string.account_new_password},
                new int[]{EMAIL,InputType.TYPE_CLASS_TEXT,PASSWORD}, f -> {
                    start(); manager.request("POST","/auth/password-reset/confirm",AccountManager.body("email",value(f[0]).trim(),
                            "verificationCode",value(f[1]).trim(),"newPassword",value(f[2])),false,true,new AccountManager.Callback<JsonObject>() {
                        public void onSuccess(JsonObject result) {
                            if (manager.needsLogin()) { OnlineSession.get().disconnect(); }
                            done();
                        }
                        public void onError(String message) { fail(message); }
                    });
                });
        LinearLayout parent=(LinearLayout)fields[0].getParent();
        formSend=OnlineUi.secondaryButton(this,R.string.account_send_code); parent.addView(formSend,new LinearLayout.LayoutParams(-1,OnlineUi.dp(this,52)));
        formSend.setOnClickListener(v -> {
            if (!Patterns.EMAIL_ADDRESS.matcher(value(fields[0]).trim()).matches()) { formError.setText(R.string.account_email_invalid); return; }
            start(); manager.request("POST","/auth/password-reset/request",AccountManager.body("email",value(fields[0]).trim()),false,false,messageCallback(false));
        });
    }
    private void profileForm() {
        EditText[] fields=form(R.string.account_profile,new int[]{R.string.account_nickname},new int[]{InputType.TYPE_CLASS_TEXT},f -> {});
        fields[0].setText(user.nickname);
        Spinner avatar=new Spinner(this); String[] values=new String[12], labels=new String[12];
        for(int i=0;i<12;i++) { values[i]="avatar_"+(i+1); labels[i]=getString(R.string.account_avatar,i+1); }
        avatar.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
        for(int i=0;i<12;i++)if(values[i].equals(user.avatarValue))avatar.setSelection(i);
        ((LinearLayout)fields[0].getParent()).addView(avatar,1);
        formSubmit.setOnClickListener(v -> {
            if(value(fields[0]).trim().isEmpty()) { formError.setText(R.string.account_required); return; }
            start(); manager.request("PATCH","/me/profile",AccountManager.body("nickname",value(fields[0]).trim(),
                    "avatarValue",values[avatar.getSelectedItemPosition()]),true,true,new AccountManager.Callback<JsonObject>() {
                public void onSuccess(JsonObject result) { accountChanged(); }
                public void onError(String message) { fail(message); }
            });
        });
    }
    private void deleteForm() {
        form(R.string.account_delete,new int[]{R.string.account_password},new int[]{PASSWORD},f -> {
            start(); manager.request("POST","/me/delete-request",AccountManager.body("password",value(f[0])),true,true,
                    new AccountManager.Callback<JsonObject>() {
                        public void onSuccess(JsonObject result) { OnlineSession.get().disconnect(); done(); }
                        public void onError(String message) { fail(message); }
                    });
        });
    }
    private void devices() {
        start(); manager.request("GET","/me/devices",null,true,false,new AccountManager.Callback<JsonObject>() {
            public void onSuccess(JsonObject response) {
                if(!active())return; done();
                LinearLayout list=OnlineUi.page(AccountActivity.this);
                JsonArray devices=response.has("devices") && response.get("devices").isJsonArray() ? response.getAsJsonArray("devices") : new JsonArray();
                if(devices.size()==0)list.addView(text(getString(R.string.account_empty_devices)));
                for(JsonElement element:devices) {
                    JsonObject device=element.getAsJsonObject();
                    String id=device.get("deviceId").getAsString();
                    boolean current=device.has("current")&&device.get("current").getAsBoolean();
                    long lastActiveAt=device.get("lastActiveAt").getAsLong();
                    String lastActiveText=java.text.DateFormat.getDateTimeInstance(
                            java.text.DateFormat.MEDIUM,java.text.DateFormat.SHORT).format(new java.util.Date(lastActiveAt));
                    list.addView(text(device.get("deviceName").getAsString()+"\n"+lastActiveText
                            +(current ? "\n"+getString(R.string.account_current_device) : "")));
                    action(list,R.string.account_revoke,()->confirm(R.string.account_logout_warning,()-> {
                        start(); manager.request("DELETE","/me/devices/"+android.net.Uri.encode(id),null,true,true,new AccountManager.Callback<JsonObject>() {
                            public void onSuccess(JsonObject value) { if(current) { OnlineSession.get().disconnect(); } done(); refresh(); }
                            public void onError(String message) { fail(message); }
                        });
                    }));
                }
                ScrollView scroll=new ScrollView(AccountActivity.this);scroll.addView(list);
                new AlertDialog.Builder(AccountActivity.this).setTitle(R.string.account_devices).setView(scroll).setNegativeButton(R.string.cancel,null).show();
            }
            public void onError(String message) { fail(message); }
        });
    }
    private void migrationForm() {
        form(R.string.account_migrate,new int[]{R.string.account_environment},new int[]{InputType.TYPE_CLASS_TEXT},f -> {
            String target=value(f[0]).trim();
            if(!target.startsWith("wss://")) { formError.setText(getString(R.string.account_environment_note)); return; }
            final String message;
            try { message=getString(R.string.account_migrate_warning,OnlineIdentity.legacyEndpoint(this),target,
                    OnlineIdentity.getUserId(this)); }
            catch(RuntimeException e) { formError.setText(e.getMessage()); return; }
            new AlertDialog.Builder(this).setTitle(R.string.account_migrate).setMessage(message)
                    .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.account_confirm,(d,w)-> {
                        start(); manager.migrateLegacyToTrustedEndpoint(target,authCallback());
                    }).show();
        });
    }
    private void environmentForm() {
        EditText[] fields=form(R.string.account_environment,new int[]{R.string.account_environment},new int[]{InputType.TYPE_CLASS_TEXT},f -> {
            try { OnlineConfig.setEndpoint(this,value(f[0]).trim()); done(); refresh(); }
            catch(RuntimeException e) { fail(e.getMessage()); }
        });
        try { fields[0].setText(OnlineConfig.getEndpoint(this)); }
        catch(RuntimeException e) { formError.setText(e.getMessage()); }
    }
}
