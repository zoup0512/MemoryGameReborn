package com.cube.memorygames.api.online;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns account credentials; database/offline LocalUser is intentionally never touched. */
public final class AccountManager {
    public interface Callback<T> { void onSuccess(T value); void onError(String message); }
    public static final class UserProfile {
        public long userId;
        public String accountType, nickname, avatarValue, email;
        public int elo, balance, wins, losses, draws;
        public java.util.Map<String, OnlineMessages.MatchStats> modeStats;
        public boolean emailVerified;
        /** Server-authoritative epoch milliseconds; zero means no previous nickname change. */
        public long nextNicknameChangeAt;
        public boolean isGuest() { return "GUEST".equalsIgnoreCase(accountType); }
    }
    public static final class AuthResponse {
        public String accessToken, refreshToken, sessionId;
        public long accessExpiresAt, refreshExpiresAt;
        public UserProfile user;
    }
    private static final class Identity {
        String environment, installationId, guestSecret;
        AuthResponse auth;
        boolean requiresLogin;
        long legacyUserId;
        String pendingEmail;
        AuthResponse previousGuest;
        String previousGuestInstallationId, previousGuestSecret;
    }
    public static final class AccountError extends Exception {
        public final int status;
        public final String code;
        AccountError(int status, String code, String message) { super(message); this.status=status; this.code=code; }
    }
    private interface Work<T> { T run() throws Exception; }
    private static AccountManager instance;
    public static synchronized AccountManager get(Context context) {
        if (instance == null) instance = new AccountManager(context.getApplicationContext());
        return instance;
    }
    private final Context context;
    private final SecureIdentityStore store;
    private final AccountRestClient rest = new AccountRestClient();
    private final Gson gson = new Gson();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile Identity identity;
    private volatile boolean loaded, busy;
    private volatile long epoch;
    private final ThreadLocal<Long> workEpoch = new ThreadLocal<>();
    private final ThreadLocal<String> workBase = new ThreadLocal<>();
    private String base() {
        String value=workBase.get();
        if (value == null) throw new IllegalStateException("账号请求必须在后台线程执行");
        checkEpoch(); return value;
    }
    private void checkEpoch() {
        Long value=workEpoch.get();
        if (value == null || value != epoch || !workBase.get().equals(OnlineConfig.apiBase(context)))
            throw new IllegalStateException("账户请求已取消：环境或身份发生变化");
    }
    private AccountManager(Context context) { this.context=context; store=new SecureIdentityStore(context); }
    public boolean isBusy() { return busy; }
    public boolean hasStoredIdentity() {
        Identity value=identity;
        return OnlineIdentity.hasLegacy(context) || store.exists();
    }
    public UserProfile profile() { Identity value=identity; return value == null || value.auth == null ? null : value.auth.user; }
    public boolean needsLogin() { return identity != null && identity.requiresLogin; }
    public boolean usesV2() { return loaded ? identity != null : store.exists(); }
    public boolean canMigrateLegacy() { return OnlineIdentity.hasLegacy(context) && (identity == null || identity.auth == null); }
    private volatile String authorizedMigrationTarget;
    public void migrateLegacyToTrustedEndpoint(String target, Callback<UserProfile> callback) {
        if (!canMigrateLegacy() || busy || !OnlineSession.get().canChangeAccount()) {
            callback.onError("仅未升级的旧游客可以授权迁移，请先结束在线活动"); return;
        }
        final String previous;
        try { previous=OnlineConfig.getEndpoint(context); OnlineConfig.selectMigrationEndpoint(context,target); }
        catch(RuntimeException e) { callback.onError(e.getMessage()); return; }
        OnlineSession.get().disconnect();
        authorizedMigrationTarget=target;
        submit(true, () -> {
            Identity existing=load();
            if (existing != null && existing.auth != null) throw new IllegalStateException("已建立v2身份不可作为旧游客迁移");
            return ensureBlocking(false);
        },new Callback<String>() {
            public void onSuccess(String token) { authorizedMigrationTarget=null; callback.onSuccess(profile()); }
            public void onError(String message) {
                authorizedMigrationTarget=null;
                // A server migration may have committed; retain secret and legacy proof, restore only when no auth saved.
                if (identity == null || identity.auth == null) OnlineConfig.restoreEndpoint(context,previous);
                callback.onError(message);
            }
        });
    }
    private Identity load() throws Exception {
        if (!loaded) {
            String json=store.read();
            Identity value=json == null ? null : gson.fromJson(json, Identity.class);
            if (json != null && (value == null || value.installationId == null || value.guestSecret == null))
                throw new IllegalStateException("安全身份损坏，凭证保留，请勿清除数据");
            identity=value; loaded=true;
        }
        Identity value=identity;
        String env=OnlineConfig.apiBase(context);
        if (value != null && value.environment != null && !env.equals(value.environment)
                && !(value.auth == null && OnlineIdentity.hasLegacy(context)
                && OnlineConfig.getEndpoint(context).equals(authorizedMigrationTarget)))
            throw new IllegalStateException("保存身份属于另一服务环境，禁止发送凭证或游客秘密；请返回原地址");
        return value;
    }
    private Identity newIdentity() {
        Identity value=new Identity(); value.environment=OnlineConfig.apiBase(context);
        value.installationId=UUID.randomUUID().toString(); value.guestSecret=SecureIdentityStore.randomSecret();
        return value;
    }
    private void persist(Identity value) throws Exception {
        checkEpoch();
        store.write(gson.toJson(value));
        context.getSharedPreferences("online_secure_identity",0).edit().putBoolean("v2Established",value.auth != null).commit();
        identity=value; loaded=true;
    }
    private void saveAuth(AuthResponse response, Identity base, long expectedUser) throws Exception {
        if (response == null || response.user == null || response.user.userId <= 0 || response.accessToken == null
                || response.refreshToken == null || response.sessionId == null || response.accessExpiresAt <= 0
                || response.refreshExpiresAt <= 0) throw new IllegalStateException("认证响应缺少必需字段");
        if (expectedUser > 0 && response.user.userId != expectedUser) throw new IllegalStateException("身份升级改变了用户ID，已拒绝响应");
        Identity next=new Identity(); next.environment=base.environment;
        next.installationId=base.installationId; next.guestSecret=base.guestSecret; next.auth=response;
        next.previousGuest=base.previousGuest;
        next.previousGuestInstallationId=base.previousGuestInstallationId;
        next.previousGuestSecret=base.previousGuestSecret;
        persist(next);
    }
    private JsonObject device(Identity value) {
        return body("installationId", value.installationId, "deviceName", Build.MANUFACTURER + " " + Build.MODEL);
    }
    private AuthResponse auth(String path, JsonObject data, String token) throws Exception {
        return gson.fromJson(rest.request(base(), "POST", path, data, token), AuthResponse.class);
    }
    private String ensureBlocking(boolean forceRefresh) throws Exception {
        OnlineConfig.requireAccountTransport(context);
        Identity value=load();
        if (value == null) {
            value=newIdentity(); persist(value); // Guest secret must be durable BEFORE the server creates an account.
        } else if (value.environment != null && !value.environment.equals(base())) {
            if (value.auth != null || !OnlineIdentity.hasLegacy(context)
                    || !OnlineConfig.getEndpoint(context).equals(authorizedMigrationTarget))
                throw new IllegalStateException("身份属于另一环境，禁止复用凭证");
            Identity next=gson.fromJson(gson.toJson(value),Identity.class);
            next.environment=base(); persist(next); value=next; // Keep recovery secret after a lost migration response.
        }
        if (value.requiresLogin) throw new IllegalStateException("会话已撤销或过期，请登录；不会自动创建新账号");
        if (value.auth != null) {
            if (!forceRefresh && value.auth.accessExpiresAt > System.currentTimeMillis()+30000L) return value.auth.accessToken;
            try {
                AuthResponse response=auth("/auth/refresh", body("refreshToken", value.auth.refreshToken), null);
                saveAuth(response, value, value.auth.user.userId);
                return response.accessToken;
            } catch (AccountError error) {
                if (error.status == 401 || error.status == 403) {
                    Identity blocked=gson.fromJson(gson.toJson(value), Identity.class); blocked.requiresLogin=true; persist(blocked);
                }
                throw error;
            }
        }
        JsonObject request=device(value);
        request.addProperty("guestSecret", value.guestSecret);
        boolean migrating=OnlineIdentity.hasLegacy(context);
        if (migrating) request.addProperty("legacyToken", authorizedMigrationTarget == null
                ? OnlineIdentity.getToken(context) : OnlineIdentity.getMigrationToken(context,authorizedMigrationTarget));
        // Omit nickname for a new guest: only the server may generate the public safe default.
        if (migrating && value.legacyUserId <= 0) {
            Identity next=gson.fromJson(gson.toJson(value),Identity.class);
            next.legacyUserId=OnlineIdentity.getUserId(context); persist(next); value=next;
        }
        AuthResponse response;
        try { response=auth(migrating ? "/auth/migrate" : "/auth/guest", request, null); }
        catch (AccountError error) {
            if (!migrating || error.status != 401 || value.legacyUserId <= 0) throw error;
            // Migration may already have committed on server. Secret recovery must keep the exact old id.
            JsonObject recover=device(value); recover.addProperty("guestSecret",value.guestSecret);
            recover.addProperty("nickname",OnlineIdentity.getNickname(context));
            response=auth("/auth/guest",recover,null);
        }
        saveAuth(response, value, migrating ? value.legacyUserId : -1L);
        if (migrating) OnlineIdentity.clearToken(context); // Only AFTER encrypted v2 credentials commit.
        return response.accessToken;
    }
    private <T> void submit(final boolean mutation, final Work<T> work, final Callback<T> callback) {
        if (mutation && (!OnlineSession.get().canChangeAccount() || busy)) { callback.onError("请先结束排队、对局、好友房或练习请求"); return; }
        if (mutation) {
            busy=true;
            OnlineSession.get().disconnect(); // No old socket frame may race an identity/profile mutation.
        }
        final long requestEpoch=mutation ? ++epoch : epoch;
        final String endpoint;
        final String capturedBase;
        try { endpoint=OnlineConfig.getEndpoint(context); capturedBase=OnlineConfig.apiBase(context); }
        catch (RuntimeException e) {
            if (mutation) busy=false;
            callback.onError(e.getMessage()); return;
        }
        worker.execute(() -> {
            workEpoch.set(requestEpoch); workBase.set(capturedBase);
            T value=null; String error=null;
            try {
                if (requestEpoch != epoch || !endpoint.equals(OnlineConfig.getEndpoint(context))) throw new IllegalStateException("请求环境已变化，请重试");
                value=work.run();
            } catch (Exception e) {
                error=e instanceof AccountError ? friendlyError((AccountError)e)
                        : e instanceof IllegalStateException ? e.getMessage() : context.getString(com.memory.brain.training.games.R.string.profile_network_error);
            }
            final T result=value; final String failure=error;
            main.post(() -> {
                if (requestEpoch != epoch || !endpoint.equals(OnlineConfig.getEndpoint(context))) {
                    if (mutation) busy=false;
                    callback.onError("请求已取消：账户或环境已变化"); return;
                }
                if (mutation) busy=false;
                if (failure == null) callback.onSuccess(result); else callback.onError(failure);
            });
        });
    }
    public void ensureAccess(boolean forceRefresh, Callback<String> callback) {
        submit(false, () -> ensureBlocking(forceRefresh), callback);
    }
    private void markRequiresLogin() throws Exception {
        Identity current=load();
        if (current == null || current.auth == null || current.requiresLogin) return;
        Identity blocked=gson.fromJson(gson.toJson(current),Identity.class);
        blocked.requiresLogin=true; persist(blocked);
    }
    private boolean credentialRejected(AccountError error, String path) {
        if (error.status != 401) return false;
        // A password validation failure is not evidence that the bearer session is invalid.
        if ("/me/delete-request".equals(path)) {
            return "ACCESS_TOKEN_EXPIRED".equals(error.code)
                    || "INVALID_ACCESS_TOKEN".equals(error.code) || "SESSION_REVOKED".equals(error.code)
                    || "TOKEN_EXPIRED".equals(error.code);
        }
        return true;
    }
    /** Retry only an explicit bearer rejection, once. Never create a replacement identity on failure. */
    private JsonObject authenticatedRequest(String method, String path, JsonObject data) throws Exception {
        String token=ensureBlocking(false);
        try { return rest.request(base(),method,path,data,token); }
        catch (AccountError first) {
            if (!credentialRejected(first,path)) throw first;
        }
        // A future local expiry timestamp is not proof that the server still recognizes the credential.
        token=ensureBlocking(true); // Explicit refresh 401/403 already persists requiresLogin.
        try { return rest.request(base(),method,path,data,token); }
        catch (AccountError last) {
            if (credentialRejected(last,path)) markRequiresLogin();
            throw last;
        }
    }
    /** UI/REST profile is not AuthOk: REST does not contain season/economy configuration. */
    public void me(Callback<UserProfile> callback) {
        submit(false, () -> {
            UserProfile user=gson.fromJson(authenticatedRequest("GET", "/me", null), UserProfile.class);
            if (user == null || user.userId != identity.auth.user.userId) throw new IllegalStateException("资料响应身份不匹配");
            Identity next=gson.fromJson(gson.toJson(identity), Identity.class); next.auth.user=user; persist(next);
            return user;
        }, callback);
    }
    /** Loads encrypted cached profile only. Opening My profile must not create an online guest. */
    public void cachedProfile(Callback<UserProfile> callback) {
        submit(false, () -> { load(); return profile(); }, callback);
    }
    /** A single atomic profile request; no optimistic cache or second avatar-only request on failure. */
    public void updateProfile(long expectedUser,String nickname,String avatar,Callback<UserProfile> callback) {
        if ((nickname!=null && !ProfileRules.validNickname(nickname))
                || (avatar!=null && !ProfileRules.validAvatar(avatar)) || (nickname==null && avatar==null)) {
            callback.onError(context.getString(com.memory.brain.training.games.R.string.profile_invalid)); return;
        }
        submit(true, () -> {
            ensureBlocking(false);
            long expected=identity.auth.user.userId;
            if(expectedUser>0 && !ProfileRules.matchesUser(expectedUser,expected))
                throw new IllegalStateException(context.getString(com.memory.brain.training.games.R.string.profile_response_error));
            JsonObject data=profilePatch(nickname,avatar);
            JsonObject response=authenticatedRequest("PATCH","/me/profile",data);
            UserProfile user=gson.fromJson(response,UserProfile.class);
            if(user==null || !ProfileRules.matchesUser(expected,user.userId) || user.nickname==null
                    || !ProfileRules.validAvatar(user.avatarValue) || (avatar!=null && !avatar.equals(user.avatarValue))
                    || (data.has("nickname") && !nickname.equals(user.nickname)))
                throw new IllegalStateException(context.getString(com.memory.brain.training.games.R.string.profile_response_error));
            Identity next=gson.fromJson(gson.toJson(identity),Identity.class); next.auth.user=user;
            persist(next); return user;
        },new Callback<UserProfile>() {
            public void onSuccess(UserProfile value) { OnlineSession.get().profileUpdated(value); callback.onSuccess(value); }
            public void onError(String message) { callback.onError(message); }
        });
    }
    static JsonObject profilePatch(String nickname,String avatar) {
        JsonObject data=new JsonObject();
        if(nickname!=null)data.addProperty("nickname",nickname);
        if(avatar!=null)data.addProperty("avatarValue",avatar);
        return data;
    }
    private String friendlyError(AccountError error) {
        String kind=ProfileRules.moderationError(error.status,error.code);
        int resource=0;
        if("rejected".equals(kind)) resource=com.memory.brain.training.games.R.string.profile_rejected;
        else if("pending".equals(kind)) resource=com.memory.brain.training.games.R.string.profile_pending;
        else if("unavailable".equals(kind)) resource=com.memory.brain.training.games.R.string.profile_unavailable;
        else if("cooldown".equals(kind)) resource=com.memory.brain.training.games.R.string.profile_cooldown_error;
        else if("invalid".equals(kind)) resource=com.memory.brain.training.games.R.string.profile_invalid;
        if(resource!=0) return context.getString(resource);
        // Never echo a moderation service's raw reason or matched words, including unknown new codes.
        if(error.code!=null && error.code.startsWith("NICKNAME_"))
            return context.getString(com.memory.brain.training.games.R.string.profile_unavailable);
        return error.code+": "+error.getMessage();
    }
    public void login(String email, String password, boolean cancelDelete, Callback<UserProfile> callback) {
        submit(true, () -> {
            OnlineConfig.requireAccountTransport(context);
            Identity value=load(); if (value == null) { value=newIdentity(); persist(value); }
            if (value.auth != null && !value.requiresLogin) {
                try { ensureBlocking(false); value=identity; }
                catch(AccountError error) {
                    if (!identity.requiresLogin) throw error;
                    value=identity; // Explicit login may replace an already server-revoked session.
                }
            }
            // Durable encrypted stash preserves the guest across a failed/lost login response.
            if (value.auth != null && value.auth.user.isGuest()) {
                Identity stashed=gson.fromJson(gson.toJson(value),Identity.class);
                stashed.previousGuest=value.auth;
                stashed.previousGuestInstallationId=value.installationId; stashed.previousGuestSecret=value.guestSecret;
                persist(stashed); value=stashed;
            }
            JsonObject request=device(value); request.addProperty("email",email); request.addProperty("password",password);
            AuthResponse response=auth(cancelDelete ? "/auth/delete-cancel" : "/auth/login",request,null);
            AuthResponse old=value.auth;
            if (old != null && !value.requiresLogin && old.user.userId != response.user.userId) {
                // Switching clears live credentials only after the original server session acknowledges revoke.
                try { rest.request(base(),"POST","/auth/logout",new JsonObject(),old.accessToken); }
                catch (Exception error) {
                    boolean revoked=error instanceof AccountError && ((AccountError)error).status==401;
                    if (!revoked) {
                        try { rest.request(base(),"POST","/auth/logout",new JsonObject(),response.accessToken); }
                        catch(Exception ignored) { }
                        throw new IllegalStateException("原账号退出未确认，保留原身份，请重试；不会合并金币或ELO");
                    }
                }
            }
            saveAuth(response,value,-1L);
            return response.user;
        }, callback);
    }
    public void register(String email, String password, String code, Callback<UserProfile> callback) {
        submit(true, () -> {
            ensureBlocking(false);
            if (!identity.auth.user.isGuest()) throw new IllegalStateException("当前已是正式账号");
            Identity pending=gson.fromJson(gson.toJson(identity),Identity.class);
            pending.pendingEmail=email; persist(pending);
            AuthResponse response;
            try { response=gson.fromJson(authenticatedRequest("POST","/auth/register",
                    body("email",email,"password",password,"verificationCode",code)),AuthResponse.class); }
            catch(AccountError error) {
                // An explicit validation rejection did not upgrade the guest. Lost responses keep the recovery hint.
                if (error.status==400 || error.status==409 || error.status==422 || error.status==429) {
                    Identity rejected=gson.fromJson(gson.toJson(identity),Identity.class); rejected.pendingEmail=null; persist(rejected);
                }
                throw error;
            }
            saveAuth(response,identity,identity.auth.user.userId); return response.user;
        },callback);
    }
    public void logout(boolean all, Callback<Boolean> callback) {
        submit(true, () -> {
            authenticatedRequest("POST",all ? "/auth/logout-all" : "/auth/logout",new JsonObject());
            Identity next=newIdentity();
            if (identity.previousGuest != null) {
                next.previousGuest=identity.previousGuest;
                next.previousGuestInstallationId=identity.previousGuestInstallationId;
                next.previousGuestSecret=identity.previousGuestSecret;
            }
            persist(next); // No clear until revoke was acknowledged; retained guest remains explicitly recoverable.
            return true;
        },callback);
    }
    public void forgetRevokedIdentity(Callback<Boolean> callback) {
        submit(true, () -> {
            Identity value=load();
            if (value == null || !value.requiresLogin) throw new IllegalStateException("有效会话必须先在服务端退出");
            persist(newIdentity()); return true;
        },callback);
    }
    public void recoverGuest(Callback<UserProfile> callback) {
        submit(true, () -> {
            OnlineConfig.requireAccountTransport(context);
            Identity value=load();
            if (value != null && value.auth != null && !value.auth.user.isGuest() && !value.requiresLogin)
                throw new IllegalStateException("请先退出当前正式账号，再恢复保留游客");
            AuthResponse old=value == null ? null : value.previousGuest != null ? value.previousGuest : value.auth;
            if (old == null || old.user == null || !old.user.isGuest() || value.pendingEmail != null)
                throw new IllegalStateException("请使用刚绑定的邮箱登录，不可创建替代游客");
            Identity recovery=gson.fromJson(gson.toJson(value),Identity.class);
            if (value.previousGuest != null && value.previousGuestSecret != null) {
                recovery.installationId=value.previousGuestInstallationId; recovery.guestSecret=value.previousGuestSecret;
            }
            JsonObject request=device(recovery); request.addProperty("guestSecret",recovery.guestSecret);
            request.addProperty("nickname",old.user.nickname);
            AuthResponse response=auth("/auth/guest",request,null);
            // A live different guest also needs acknowledged revoke before replacement.
            if (value.auth != null && !value.requiresLogin && value.auth.user.userId != old.user.userId) {
                String token=ensureBlocking(false);
                rest.request(base(),"POST","/auth/logout",new JsonObject(),token);
            }
            saveAuth(response,recovery,old.user.userId); return response.user;
        },callback);
    }
    public void request(String method, String path, JsonObject data, boolean authenticated, boolean destructive, Callback<JsonObject> callback) {
        submit(destructive, () -> {
            OnlineConfig.requireAccountTransport(context);
            JsonObject result=authenticated ? authenticatedRequest(method,path,data)
                    : rest.request(base(),method,path,data,null);
            if ("/auth/password-reset/confirm".equals(path) && identity != null && identity.auth != null
                    && identity.auth.user.email != null && data.has("email")
                    && identity.auth.user.email.equalsIgnoreCase(data.get("email").getAsString())) {
                Identity next=gson.fromJson(gson.toJson(identity),Identity.class); next.requiresLogin=true; persist(next);
            }
            if ("/me/delete-request".equals(path)) {
                // Server revokes sessions on successful delete request. Keep account identity for explicit cancel/login.
                Identity next=gson.fromJson(gson.toJson(identity),Identity.class); next.requiresLogin=true; persist(next);
            }
            return result;
        },callback);
    }
    /** Diagnostics are non-destructive and may only reuse an already authenticated identity. */
    public void uploadDiagnostics(String expectedBase, JsonObject data, Callback<JsonObject> callback) {
        final boolean destructive=false;
        submit(destructive, () -> {
            com.cube.memorygames.diagnostics.DiagnosticUploadPolicy.requireHttps(expectedBase);
            if (!expectedBase.equals(base())) throw new IllegalStateException("诊断服务环境已变化，请重新确认");
            Identity value=load();
            if (value == null || value.auth == null || value.requiresLogin)
                throw new IllegalStateException("请先进入在线大厅建立游客或登录账号，再上传日志");
            return authenticatedRequest("POST", "/diagnostics/uploads", data);
        }, callback);
    }
    public static JsonObject body(String... pairs) {
        JsonObject json=new JsonObject(); for(int i=0;i<pairs.length;i+=2) json.addProperty(pairs[i],pairs[i+1]); return json;
    }
}
