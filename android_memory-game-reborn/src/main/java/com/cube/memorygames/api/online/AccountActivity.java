package com.cube.memorygames.api.online;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.graphics.drawable.GradientDrawable;
import com.cube.memorygames.ui.DialogUi;
import java.util.Locale;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.content.DialogInterface;
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
    private TextView formTechnical;
    private TextView statusTechnical;
    private EditText[] formFields;
    private int[] formHints;
    private AlertDialog confirmation;
    private EditText profileNickname;
    private String profileOriginalNickname, profileOriginalAvatar, profileAvatar;
    private boolean profileEditing, profileSaving, restoredDraft;
    private long profileUserId;
    private String restoredNickname, restoredAvatar;
    private boolean restoredOnline;
    private final android.os.Handler cooldownHandler=new android.os.Handler();
    private final Runnable cooldownTick=new Runnable() { public void run() {
        if(profileEditing && active()) { updateCooldown(); cooldownHandler.postDelayed(this,1000); }
    }};
    private TextView cooldownText, inspiration;
    private final java.util.List<View> avatarChoices=new java.util.ArrayList<>();
    private final java.util.List<TextView> avatarChecks=new java.util.ArrayList<>();
    private interface FormAction { void run(EditText[] fields); }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        OnlineSession.get().init(this);
        manager=AccountManager.get(this);
        user=manager.profile();
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(OnlineUi.color(this,R.color.online_page));
        root.addView(OnlineUi.toolbar(this,R.string.online_account_title,v -> onBackPressed()).view);
        ScrollView scroll=new ScrollView(this);
        page=OnlineUi.page(this); scroll.addView(page);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
        if(state!=null && state.getBoolean("profileEditing")) {
            restoredDraft=true; restoredNickname=state.getString("profileNickname");
            restoredAvatar=state.getString("profileAvatar"); profileUserId=state.getLong("profileUserId");
            restoredOnline=state.getBoolean("profileOnline");
        }
        render();
        loading=true;
        manager.cachedProfile(new AccountManager.Callback<AccountManager.UserProfile>() {
            public void onSuccess(AccountManager.UserProfile value) {
                if(!active())return; loading=false; user=value; render();
                if(restoredDraft && (restoredOnline ? user!=null && user.userId==profileUserId : user==null)) profileForm();
                else { restoredDraft=false; if(value!=null) refresh(); }
            }
            public void onError(String message) { restoredDraft=false; fail(message); }
        });
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if(profileEditing && profileNickname!=null) {
            state.putBoolean("profileEditing",true); state.putString("profileNickname",value(profileNickname));
            state.putString("profileAvatar",profileAvatar); state.putLong("profileUserId",profileUserId);
            state.putBoolean("profileOnline",user!=null);
        }
    }
    @Override protected void onDestroy() { cooldownHandler.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override public void onBackPressed() {
        if(loading)return;
        if(profileEditing) { dismissProfile(); return; }
        super.onBackPressed();
    }
    private boolean active() { return !isFinishing() && !isDestroyed(); }
    private TextView text(String value) {
        TextView view=OnlineUi.text(this,value,14,R.color.online_text_secondary,OnlineUi.regular(this));
        view.setPadding(0,OnlineUi.dp(this,8),0,OnlineUi.dp(this,8)); return view;
    }
    private void action(LinearLayout parent,int label,Runnable runnable) {
        TextView button=OnlineUi.secondaryButton(this,label);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.topMargin=OnlineUi.dp(this,8);
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
            OnlineUi.AvatarBlock avatar=OnlineUi.avatar(this,64,false);
            OnlineUi.applyAvatar(this,avatar,old==null ? LocalProfile.avatar(this) : old.avatarValue); card.addView(avatar.view);
            card.addView(text(old==null ? LocalProfile.nickname(this)+"\n"+getString(R.string.profile_local_note)
                    : getString(R.string.account_guest)+" #"+old.userId+"\n"+old.nickname));
        }
        card.addView(text(getString(user == null || user.isGuest() ? R.string.account_warning : R.string.account_registered_note)));
        status=text(loading ? getString(R.string.account_loading) : "");
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        card.addView(status);
        statusTechnical=text(""); statusTechnical.setTextSize(12);
        statusTechnical.setVisibility(View.GONE); card.addView(statusTechnical);
        page.addView(card,OnlineUi.cardParams(this));
        LinearLayout actions=OnlineUi.card(this);
        action(actions,R.string.account_profile,this::profileForm);
        action(actions,R.string.account_retry,this::refresh);
        if (user == null || user.isGuest()) action(actions,R.string.account_bind,this::bindForm);
        action(actions,R.string.account_login,() -> confirm(R.string.account_login, R.string.dialog_account_continue_login, R.string.account_login_warning, false, () -> loginForm(false)));
        if (user != null) {
            action(actions,R.string.account_devices,this::devices);
            action(actions,R.string.account_logout,() -> confirm(R.string.account_logout, R.string.dialog_account_logout, R.string.account_logout_warning, true, () -> logout(false)));
            if (!user.isGuest()) {
                action(actions,R.string.account_logout_all,() -> confirm(R.string.account_logout_all, R.string.account_logout_all, R.string.dialog_account_logout_all_warning, true, () -> logout(true)));
                action(actions,R.string.account_delete,() -> confirm(R.string.account_delete, R.string.dialog_account_delete, R.string.account_delete_warning, true, this::deleteForm));
            }
        }
        if (manager.needsLogin()) action(actions,R.string.account_forget,() -> confirm(R.string.account_forget, R.string.account_forget, R.string.account_forget_warning, true, () -> {
            start(); manager.forgetRevokedIdentity(new AccountManager.Callback<Boolean>() {
                public void onSuccess(Boolean value) { accountChanged(); }
                public void onError(String message) { fail(message); }
            });
        }));
        action(actions,R.string.account_recover_guest,() -> confirm(R.string.account_recover_guest, R.string.account_recover_guest, R.string.account_recover_warning, false, () -> {
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
        setFormLoading(true);
        if (formError != null) formError.setText(R.string.account_loading);
        if (formTechnical != null) formTechnical.setVisibility(View.GONE);
        render();
    }
    private void setFormLoading(boolean busy) {
        if (formSubmit != null) formSubmit.setEnabled(!busy);
        if (formSend != null) formSend.setEnabled(!busy);
        if (formFields != null) for (EditText field : formFields) field.setEnabled(!busy);
        if (form != null) {
            form.setCancelable(!busy && !profileEditing);
            form.setCanceledOnTouchOutside(false);
            // setCancelable does not disable AlertDialog button auto-dismissal.
            if (form.getButton(AlertDialog.BUTTON_NEGATIVE) != null)
                form.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!busy);
        }
    }
    private void fail(String message) {
        if (!active()) return;
        loading=false;
        // Retain persisted identity and recovery actions after failed /me or authentication.
        user=manager.profile();
        if (profileEditing && profileUserId==0 && user!=null) {
            profileUserId=user.userId;
            // Guest creation can precede a rejected PATCH; keep any custom nickname/avatar draft.
            if (ProfileRules.same(value(profileNickname),LocalProfile.nickname(this))) {
                profileNickname.setText(user.nickname); profileOriginalNickname=user.nickname;
            }
        }
        render();
        String friendly=getString(errorResource(message));
        String code=technicalCode(message);
        status.setText(friendly);
        showTechnical(statusTechnical,code);
        if (formError != null) {
            formError.setText(friendly);
            formError.setTextColor(OnlineUi.color(this,R.color.dialog_danger));
            showTechnical(formTechnical,code);
            markErrorField(code,friendly);
        }
        setFormLoading(false);
        if (profileEditing) {
            profileSaving=false; formSubmit.setText(R.string.profile_save);
            updateCooldown(); updateAvatarSelection();
        }
    }
    private void showTechnical(TextView view,String code) {
        if (view == null) return;
        view.setVisibility(code.isEmpty() ? View.GONE : View.VISIBLE);
        view.setText(code.isEmpty() ? "" : getString(R.string.dialog_account_technical,code));
    }
    private String technicalCode(String message) {
        if (message == null) return "";
        String code=message.split(":",2)[0].trim();
        return code.matches("[A-Z][A-Z0-9_]{2,63}") ? code : "";
    }
    private int errorResource(String message) {
        // AccountManager already sanitizes moderation results. Preserve those localized
        // messages rather than turning every rejected/pending review into "unknown".
        int[] profileErrors={R.string.profile_rejected,R.string.profile_pending,R.string.profile_unavailable,
                R.string.profile_cooldown_error,R.string.profile_invalid,R.string.profile_response_error,
                R.string.profile_network_error};
        for (int resource : profileErrors) if (getString(resource).equals(message)) return resource;
        String code=technicalCode(message);
        switch (code) {
            case "INVALID_CREDENTIALS": return R.string.dialog_account_error_credentials;
            case "INVALID_VERIFICATION_CODE": return R.string.dialog_account_error_code;
            case "INVALID_EMAIL": return R.string.account_email_invalid;
            case "EMAIL_TAKEN": return R.string.dialog_account_error_email_taken;
            case "INVALID_PASSWORD": return R.string.dialog_account_error_password;
            case "INVALID_NICKNAME": return R.string.dialog_account_error_nickname;
            case "INVALID_AVATAR": return R.string.dialog_account_error_avatar;
            case "INVALID_INPUT": return R.string.dialog_account_error_input;
            case "RATE_LIMITED": return R.string.dialog_account_error_rate;
            case "ACCOUNT_IN_MATCH": return R.string.online_account_busy;
            case "ACCOUNT_DELETED": return R.string.dialog_account_error_deleted;
            case "ACCOUNT_PENDING_DELETION": return R.string.dialog_account_error_pending;
            case "ACCOUNT_BANNED": return R.string.dialog_account_error_banned;
            case "ACCESS_TOKEN_EXPIRED": case "INVALID_ACCESS_TOKEN": case "SESSION_REVOKED":
            case "TOKEN_EXPIRED": return R.string.dialog_account_error_session;
            case "ACCOUNT_CONFLICT": return R.string.dialog_account_error_conflict;
            default: break;
        }
        String raw=message == null ? "" : message;
        String lower=raw.toLowerCase(Locale.US);
        if (raw.contains("另一环境") || raw.contains("另一服务环境")) return R.string.dialog_account_error_environment;
        if (raw.contains("环境已变化") || raw.contains("身份发生变化") || raw.contains("账户或环境已变化")) return R.string.dialog_account_error_changed;
        if (raw.contains("会话已撤销") || raw.contains("会话已过期")) return R.string.dialog_account_error_session;
        if (raw.contains("原账号退出未确认")) return R.string.dialog_account_error_logout;
        if (raw.contains("请先结束") || raw.contains("账户请求")) return R.string.online_account_busy;
        if (raw.contains("安全身份") || raw.contains("身份不匹配") || raw.contains("用户ID")
                || raw.contains("认证响应") || raw.contains("保存失败") || raw.contains("恢复失败")) return R.string.dialog_account_error_storage;
        if (raw.contains("游客") || raw.contains("正式账号") || raw.contains("刚绑定的邮箱")) return R.string.dialog_account_error_guest;
        if (raw.contains("地址") || raw.contains("HTTPS/WSS") || lower.contains("wss/https")) return R.string.dialog_account_error_endpoint;
        if (raw.contains("网络") || raw.contains("存储") || lower.contains("timeout") || lower.contains("connect")) return R.string.dialog_account_error_network;
        return R.string.dialog_account_error_unknown;
    }
    private void markErrorField(String code,String friendly) {
        if (formFields == null || formHints == null) return;
        int hint=0;
        if ("INVALID_EMAIL".equals(code) || "EMAIL_TAKEN".equals(code)) hint=R.string.account_email;
        else if ("INVALID_VERIFICATION_CODE".equals(code)) hint=R.string.account_code;
        else if ("INVALID_NICKNAME".equals(code)) hint=R.string.account_nickname;
        else if ("INVALID_PASSWORD".equals(code)) hint=R.string.account_password;
        for (int i=0;i<formHints.length;i++) if (formHints[i]==hint
                || (hint==R.string.account_password && formHints[i]==R.string.account_new_password)) {
            formFields[i].setError(friendly); break;
        }
    }
    private void done() {
        if (!active()) return;
        loading=false; closeForm(); render(); status.setText(R.string.account_success);
    }
    private void closeForm() {
        if (confirmation != null) confirmation.dismiss();
        if (form != null) form.dismiss();
        clearForm();
    }
    private void clearForm() {
        cooldownHandler.removeCallbacks(cooldownTick);
        profileEditing=false; profileSaving=false; profileNickname=null;
        cooldownText=null; inspiration=null; avatarChoices.clear(); avatarChecks.clear();
        form=null; formError=null; formTechnical=null; formSubmit=null; formSend=null;
        formFields=null; formHints=null;
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
    private void confirm(int title,int positive,int message,boolean danger,Runnable action) {
        confirm(title,positive,getString(message),danger,action);
    }
    private void confirm(int title,int positive,String message,boolean danger,Runnable action) {
        if (loading || (confirmation != null && confirmation.isShowing())) return;
        final AlertDialog dialog=DialogUi.builder(this).setTitle(title).setMessage(message)
                .setNegativeButton(R.string.cancel,null).setPositiveButton(positive,null).create();
        confirmation=dialog;
        dialog.setOnDismissListener(d -> { if (confirmation==dialog) confirmation=null; });
        dialog.show();
        if (danger) dialog.getButton(AlertDialog.BUTTON_POSITIVE).post(() -> {
            // DialogUi applies its shared primary styling after window attachment.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setBackgroundColor(android.graphics.Color.TRANSPARENT);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(OnlineUi.color(this,R.color.dialog_danger));
        });
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (loading) return;
            dialog.dismiss();
            action.run();
        });
    }
    private LinearLayout dialogContent() {
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(OnlineUi.color(this,R.color.dialog_surface));
        int padding=OnlineUi.dp(this,24);
        content.setPadding(padding,OnlineUi.dp(this,8),padding,padding);
        return content;
    }
    private TextView label(int res,View target) {
        TextView label=OnlineUi.text(this,getString(res),15,R.color.dialog_text,OnlineUi.medium(this));
        label.setPadding(0,OnlineUi.dp(this,12),0,OnlineUi.dp(this,6));
        if (target.getId()==View.NO_ID) target.setId(View.generateViewId());
        label.setLabelFor(target.getId());
        return label;
    }
    private EditText field(LinearLayout container,int hint,int type) {
        EditText field=new EditText(this); field.setInputType(type); field.setSingleLine(true);
        if ((type & InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD) {
            field.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
        }
        field.setTextColor(OnlineUi.color(this,R.color.dialog_text));
        field.setHintTextColor(OnlineUi.color(this,R.color.dialog_text_secondary));
        field.setTextSize(16);
        field.setTypeface(OnlineUi.regular(this));
        GradientDrawable background=OnlineUi.roundedBackground(this,R.color.dialog_surface,8);
        background.setStroke(OnlineUi.dp(this,1),OnlineUi.color(this,R.color.dialog_text_secondary));
        field.setBackgroundTintList(null);
        field.setBackground(background);
        field.setPadding(OnlineUi.dp(this,12),OnlineUi.dp(this,12),OnlineUi.dp(this,12),OnlineUi.dp(this,12));
        field.setMinimumHeight(OnlineUi.dp(this,48));
        field.setSaveEnabled(false); // Passwords/codes are never serialized to savedInstanceState.
        container.addView(label(hint,field),new LinearLayout.LayoutParams(-1,-2));
        container.addView(field,new LinearLayout.LayoutParams(-1,-2));
        if (hint==R.string.account_code || hint==R.string.account_new_password) {
            TextView help=text(getString(hint==R.string.account_code ? R.string.dialog_account_code_hint : R.string.dialog_account_password_hint));
            help.setTextColor(OnlineUi.color(this,R.color.dialog_text_secondary)); container.addView(help);
        }
        return field;
    }
    private int submitLabel(int title) {
        if (title==R.string.account_login) return R.string.dialog_account_login;
        if (title==R.string.account_bind) return R.string.dialog_account_register;
        if (title==R.string.account_profile || title==R.string.account_environment) return R.string.dialog_account_save;
        if (title==R.string.account_delete) return R.string.dialog_account_delete;
        if (title==R.string.account_cancel_delete) return R.string.dialog_account_restore;
        return title;
    }
    private EditText[] form(int title,int[] hints,int[] types,FormAction action) {
        closeForm();
        LinearLayout content=dialogContent();
        EditText[] fields=new EditText[hints.length];
        for(int i=0;i<hints.length;i++) fields[i]=field(content,hints[i],types[i]);
        formFields=fields; formHints=hints;
        formError=text(""); formError.setTextColor(OnlineUi.color(this,R.color.dialog_danger));
        formError.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); content.addView(formError);
        formTechnical=text(""); formTechnical.setTextSize(12);
        formTechnical.setTextColor(OnlineUi.color(this,R.color.dialog_text_secondary));
        formTechnical.setVisibility(View.GONE); content.addView(formTechnical);
        formSubmit=OnlineUi.primaryButton(this,submitLabel(title));
        if (title==R.string.account_delete) {
            formSubmit.setTextColor(OnlineUi.color(this,R.color.dialog_danger));
            formSubmit.setBackgroundResource(R.drawable.online_secondary_button);
        }
        content.addView(formSubmit,new LinearLayout.LayoutParams(-1,-2));
        formSubmit.setOnClickListener(v -> {
            if (loading || !validate(fields,hints)) return;
            action.run(fields);
        });
        ScrollView scroll=new ScrollView(this); scroll.addView(content);
        final AlertDialog dialog=DialogUi.builder(this).setTitle(title).setView(scroll)
                .setNegativeButton(R.string.cancel,null).create();
        form=dialog;
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(d -> { if (form==dialog) clearForm(); });
        dialog.show();
        return fields;
    }
    private boolean validate(EditText[] fields,int[] hints) {
        for (EditText field:fields) field.setError(null);
        for (int i=0;i<fields.length;i++) {
            int error=0;
            if (value(fields[i]).trim().isEmpty()) error=R.string.account_required;
            else if (hints[i]==R.string.account_email && !Patterns.EMAIL_ADDRESS.matcher(value(fields[i]).trim()).matches())
                error=R.string.account_email_invalid;
            if (error!=0) { fieldError(fields[i],error); return false; }
        }
        if (formTechnical!=null) formTechnical.setVisibility(View.GONE);
        return true;
    }
    private void fieldError(EditText field,int error) {
        field.setError(getString(error)); field.requestFocus();
        if (formError!=null) {
            formError.setTextColor(OnlineUi.color(this,R.color.dialog_danger));
            formError.setText(error); formError.announceForAccessibility(getString(error));
        }
        if (formTechnical!=null) formTechnical.setVisibility(View.GONE);
    }
    private void addSendCode(EditText[] fields,Runnable send) {
        LinearLayout parent=(LinearLayout)fields[0].getParent();
        EditText code=null;
        for (int i=0;i<formHints.length;i++) if (formHints[i]==R.string.account_code) code=fields[i];
        formSend=OnlineUi.secondaryButton(this,R.string.account_send_code);
        // Place the send action immediately before the permanent verification-code label.
        int index=code==null ? parent.indexOfChild(formError) : parent.indexOfChild(code)-1;
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.topMargin=OnlineUi.dp(this,12);
        parent.addView(formSend,Math.max(0,index),params);
        formSend.setOnClickListener(v -> {
            if (loading) return;
            if (!Patterns.EMAIL_ADDRESS.matcher(value(fields[0]).trim()).matches()) {
                fieldError(fields[0],R.string.account_email_invalid); return;
            }
            send.run();
        });
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
        addSendCode(fields,() -> {
            start(); manager.request("POST","/account/email-verification",AccountManager.body("email",value(fields[0]).trim()),true,false,
                    messageCallback(false));
        });
    }
    private AccountManager.Callback<JsonObject> messageCallback(boolean close) {
        return new AccountManager.Callback<JsonObject>() {
            public void onSuccess(JsonObject value) {
                if (!active()) return;
                if (close) done();
                else {
                    loading=false; render(); setFormLoading(false);
                    if(formError!=null) {
                        formError.setTextColor(OnlineUi.color(AccountActivity.this,R.color.dialog_text_secondary));
                        formError.setText(R.string.dialog_account_code_sent);
                    }
                    if(formTechnical!=null)formTechnical.setVisibility(View.GONE);
                }
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
        addSendCode(fields,() -> {
            start(); manager.request("POST","/auth/password-reset/request",AccountManager.body("email",value(fields[0]).trim()),false,false,messageCallback(false));
        });
    }
    private void profileForm() {
        closeForm(); profileEditing=true; profileUserId=user==null ? 0 : user.userId;
        profileOriginalNickname=user==null ? LocalProfile.draft(this) : user.nickname;
        profileOriginalAvatar=user==null ? LocalProfile.avatar(this) : user.avatarValue;
        profileAvatar=restoredDraft ? restoredAvatar : profileOriginalAvatar;
        LinearLayout content=dialogContent();
        final OnlineUi.AvatarBlock preview=OnlineUi.avatar(this,80,false);
        OnlineUi.applyAvatar(this,preview,profileAvatar);
        LinearLayout.LayoutParams previewParams=new LinearLayout.LayoutParams(OnlineUi.dp(this,80),OnlineUi.dp(this,80));
        previewParams.gravity=Gravity.CENTER_HORIZONTAL; content.addView(preview.view,previewParams);
        profileNickname=field(content,R.string.account_nickname,InputType.TYPE_CLASS_TEXT);
        formFields=new EditText[]{profileNickname}; formHints=new int[]{R.string.account_nickname};
        profileNickname.setText(restoredDraft ? restoredNickname : profileOriginalNickname); restoredDraft=false;
        profileNickname.setContentDescription(getString(R.string.account_nickname));
        profileNickname.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { updateProfileSubmit(); }
            public void afterTextChanged(android.text.Editable s) {}
        });
        content.addView(text(getString(R.string.profile_format)));
        cooldownText=text(""); content.addView(cooldownText);
        inspiration=OnlineUi.secondaryButton(this,R.string.profile_inspiration);
        content.addView(inspiration,new LinearLayout.LayoutParams(-1,OnlineUi.dp(this,48)));
        inspiration.setOnClickListener(v -> { if(!loading)profileNickname.setText(ProfileRules.inspiration(new java.security.SecureRandom())); });
        content.addView(text(getString(R.string.profile_inspiration_note)));
        TextView avatarLabel=OnlineUi.text(this,getString(R.string.dialog_account_avatar_label),15,
                R.color.dialog_text,OnlineUi.medium(this));
        avatarLabel.setPadding(0,OnlineUi.dp(this,12),0,OnlineUi.dp(this,6)); content.addView(avatarLabel);
        for(int row=0;row<3;row++) {
            LinearLayout line=new LinearLayout(this); content.addView(line,new LinearLayout.LayoutParams(-1,-2));
            for(int col=0;col<4;col++) {
                final int index=row*4+col;
                FrameLayout choice=new FrameLayout(this); choice.setPadding(4,4,4,4);
                ImageView image=new ImageView(this); AvatarCatalog.apply(this,image,AvatarCatalog.value(index));
                image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                choice.addView(image,new FrameLayout.LayoutParams(-1,-1));
                TextView check=new TextView(this); check.setText("✓"); check.setTextSize(18);
                check.setTextColor(android.graphics.Color.WHITE); check.setGravity(Gravity.CENTER);
                check.setBackgroundResource(R.drawable.online_primary_button);
                choice.addView(check,new FrameLayout.LayoutParams(OnlineUi.dp(this,24),OnlineUi.dp(this,24),Gravity.RIGHT|Gravity.BOTTOM));
                choice.setContentDescription(getString(R.string.profile_choose_avatar,AvatarCatalog.label(this,index)));
                choice.setFocusable(true); choice.setClickable(true);
                choice.setOnClickListener(v -> { if(loading)return; profileAvatar=AvatarCatalog.value(index);
                    OnlineUi.applyAvatar(this,preview,profileAvatar); updateAvatarSelection(); });
                avatarChoices.add(choice); avatarChecks.add(check);
                line.addView(choice,new LinearLayout.LayoutParams(0,OnlineUi.dp(this,68),1));
            }
        }
        if(user==null) content.addView(text(getString(R.string.profile_online_required)));
        formError=text(""); formError.setTextColor(OnlineUi.color(this,R.color.dialog_danger));
        formError.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); content.addView(formError);
        formTechnical=text(""); formTechnical.setTextSize(12);
        formTechnical.setTextColor(OnlineUi.color(this,R.color.dialog_text_secondary));
        formTechnical.setVisibility(View.GONE); content.addView(formTechnical);
        formSubmit=OnlineUi.primaryButton(this,R.string.profile_save);
        content.addView(formSubmit,new LinearLayout.LayoutParams(-1,OnlineUi.dp(this,52)));
        formSubmit.setOnClickListener(v -> saveProfile());
        if(user==null) {
            TextView local=OnlineUi.secondaryButton(this,R.string.profile_local_draft);
            formSend=local; // Disable device-only saving along with all other actions during the PATCH.
            content.addView(local,new LinearLayout.LayoutParams(-1,OnlineUi.dp(this,52)));
            local.setOnClickListener(v -> {
                if(loading)return;
                if(!ProfileRules.validNickname(value(profileNickname))) { formError.setText(R.string.profile_invalid); return; }
                if(LocalProfile.saveDraft(this,ProfileRules.normalize(value(profileNickname)),profileAvatar)) {
                    closeForm(); render(); status.setText(R.string.profile_draft_saved);
                } else formError.setText(R.string.profile_local_failed);
            });
        }
        ScrollView scroll=new ScrollView(this); scroll.addView(content);
        final AlertDialog dialog=DialogUi.builder(this).setTitle(R.string.account_profile).setView(scroll)
                .setNegativeButton(R.string.cancel,null).create();
        form=dialog;
        dialog.setOnDismissListener(d -> { if (form==dialog) clearForm(); });
        form.setCancelable(false); form.setCanceledOnTouchOutside(false); form.show();
        form.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(v -> dismissProfile());
        form.setOnKeyListener((d,key,event) -> {
            if(key==android.view.KeyEvent.KEYCODE_BACK && event.getAction()==android.view.KeyEvent.ACTION_UP) { dismissProfile(); return true; }
            return key==android.view.KeyEvent.KEYCODE_BACK;
        });
        updateAvatarSelection(); cooldownHandler.post(cooldownTick);
    }
    private void updateAvatarSelection() {
        for(int i=0;i<avatarChoices.size();i++) {
            boolean selected=ProfileRules.avatarIndex(profileAvatar)==i;
            avatarChoices.get(i).setSelected(selected); avatarChoices.get(i).setEnabled(!loading);
            avatarChecks.get(i).setVisibility(selected ? View.VISIBLE : View.GONE);
        }
        updateProfileSubmit();
    }
    private void updateProfileSubmit() {
        if(!profileEditing || formSubmit==null || profileNickname==null)return;
        String change=ProfileRules.nicknameChange(profileOriginalNickname,value(profileNickname));
        boolean changed=change!=null || !ProfileRules.same(profileAvatar,profileOriginalAvatar);
        // Before an online identity exists even an untouched draft can be submitted for its FIRST review.
        formSubmit.setEnabled(!loading && !profileSaving && (user==null || changed));
    }
    private void updateCooldown() {
        if(!profileEditing || profileNickname==null)return;
        boolean cooling=user!=null && ProfileRules.coolingDown(user.nextNicknameChangeAt,System.currentTimeMillis());
        profileNickname.setEnabled(!loading && !cooling); inspiration.setEnabled(!loading && !cooling);
        cooldownText.setText(cooling ? getString(R.string.profile_cooldown,
                java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(user.nextNicknameChangeAt)))
                : getString(R.string.profile_cooldown_ready));
    }
    private void dismissProfile() {
        if(loading || !profileEditing || profileNickname==null)return;
        if(!ProfileRules.same(profileOriginalNickname,value(profileNickname)) || !ProfileRules.same(profileOriginalAvatar,profileAvatar)) {
            confirm(R.string.account_profile,R.string.profile_discard,R.string.profile_unsaved,true,this::closeForm);
        } else closeForm();
    }
    private void saveProfile() {
        if(loading || profileSaving)return;
        final String nickname=ProfileRules.nicknameChange(user==null ? LocalProfile.nickname(this) : user.nickname,value(profileNickname));
        final String avatar=profileAvatar;
        boolean changed=nickname!=null;
        if(changed && !ProfileRules.validNickname(nickname)) { formError.setText(R.string.profile_invalid); return; }
        if(user!=null && changed && ProfileRules.coolingDown(user.nextNicknameChangeAt,System.currentTimeMillis())) {
            formError.setText(R.string.profile_cooldown_error); return;
        }
        if(!OnlineSession.get().canChangeAccount()) { formError.setText(R.string.online_account_busy); return; }
        profileSaving=true; start(); formSubmit.setText(R.string.profile_saving); updateCooldown(); updateAvatarSelection();
        manager.updateProfile(profileUserId,changed ? nickname : null,avatar,new AccountManager.Callback<AccountManager.UserProfile>() {
            public void onSuccess(AccountManager.UserProfile value) {
                user=value;
                if(!active())return;
                done(); OnlineSession.get().connect();
            }
            public void onError(String message) { fail(message); }
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
                LinearLayout list=dialogContent();
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
                    action(list,R.string.account_revoke,()->confirm(R.string.account_revoke,R.string.account_revoke,
                            R.string.dialog_account_revoke_warning,true,()-> {
                        start(); manager.request("DELETE","/me/devices/"+android.net.Uri.encode(id),null,true,true,new AccountManager.Callback<JsonObject>() {
                            public void onSuccess(JsonObject value) { if(current) { OnlineSession.get().disconnect(); } done(); refresh(); }
                            public void onError(String message) { fail(message); }
                        });
                    }));
                }
                ScrollView scroll=new ScrollView(AccountActivity.this);scroll.addView(list);
                final AlertDialog dialog=DialogUi.builder(AccountActivity.this).setTitle(R.string.account_devices)
                        .setView(scroll).setNegativeButton(R.string.cancel,null).create();
                form=dialog;
                dialog.setOnDismissListener(d -> { if (form==dialog) clearForm(); });
                dialog.show();
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
            catch(RuntimeException e) { fail(e.getMessage()); return; }
            confirm(R.string.account_migrate,R.string.account_migrate,message,true,() -> {
                start(); manager.migrateLegacyToTrustedEndpoint(target,authCallback());
            });
        });
    }
    private void environmentForm() {
        EditText[] fields=form(R.string.account_environment,new int[]{R.string.account_environment},new int[]{InputType.TYPE_CLASS_TEXT},f -> {
            try { OnlineConfig.setEndpoint(this,value(f[0]).trim()); done(); refresh(); }
            catch(RuntimeException e) { fail(e.getMessage()); }
        });
        try { fields[0].setText(OnlineConfig.getEndpoint(this)); }
        catch(RuntimeException e) { fail(e.getMessage()); }
    }
}
