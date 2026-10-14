package com.cube.memorygames.api.online;

import android.content.Context;
import com.memory.brain.training.games.BuildConfig;
import com.cube.memorygames.security.SecurePrefs;
import java.net.URI;

/** Release endpoints are build-time pinned; only an explicitly trusted TLS migration may change them. */
public final class OnlineConfig {
    public static final String LEGACY_ENDPOINT = "ws://124.220.35.87:8091/ws";
    public static final String DEFAULT_ENDPOINT = BuildConfig.ONLINE_ENDPOINT;
    public static final String LAN_TEST_ENDPOINT = DEFAULT_ENDPOINT;
    private static final String PREFS = "online_config";
    private static final String KEY_ENDPOINT = "endpoint";
    private OnlineConfig() { }
    private static boolean debug(Context context) {
        return BuildConfig.DEBUG && (context.getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
    private static URI endpoint(String value) {
        try {
            URI uri = new URI(value);
            if (!("ws".equals(uri.getScheme()) || "wss".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !"/ws".equals(uri.getPath())) throw new IllegalArgumentException();
            return uri;
        } catch (Exception e) { throw new IllegalArgumentException("地址必须是 ws[s]://host[:port]/ws"); }
    }
    public static String getEndpoint(Context context) {
        if (debug(context)) {
            String override = context.getSharedPreferences(PREFS,0).getString(KEY_ENDPOINT,null);
            if (override != null && !override.isEmpty()) return endpoint(override).toString();
        }
        String trusted = SecurePrefs.getSealedStrict(context,PREFS,"trustedMigrationEndpoint");
        return trusted == null ? DEFAULT_ENDPOINT : endpoint(trusted).toString();
    }
    public static String apiBase(Context context) {
        URI uri = endpoint(getEndpoint(context));
        try {
            return new URI("wss".equals(uri.getScheme()) ? "https" : "http",null,
                    uri.getHost().toLowerCase(java.util.Locale.US),uri.getPort(),"/api/v2",null,null).toString();
        } catch (Exception e) { throw new IllegalArgumentException("无效账号地址",e); }
    }
    static String origin(String value) {
        URI uri=endpoint(value);
        int port=uri.getPort() < 0 ? ("wss".equals(uri.getScheme()) ? 443 : 80) : uri.getPort();
        return uri.getScheme()+"://"+uri.getHost().toLowerCase(java.util.Locale.US)+":"+port;
    }
    private static boolean localHost(String host) {
        host=host.toLowerCase(java.util.Locale.US);
        if ("localhost".equals(host) || "[::1]".equals(host) || "::1".equals(host)) return true;
        String[] parts=host.split("\\.");
        if (parts.length != 4) return false;
        try {
            int[] n=new int[4];
            for(int i=0;i<4;i++) { n[i]=Integer.parseInt(parts[i]); if(n[i]<0 || n[i]>255)return false; }
            return n[0]==127 || n[0]==10 || (n[0]==192 && n[1]==168)
                    || (n[0]==172 && n[1]>=16 && n[1]<=31);
        } catch(NumberFormatException e) { return false; }
    }
    public static boolean accountsAllowed(Context context) {
        URI uri=endpoint(getEndpoint(context));
        return "wss".equals(uri.getScheme()) || (debug(context) && localHost(uri.getHost())
                && (hasOverride(context) || !DEFAULT_ENDPOINT.equals(LEGACY_ENDPOINT)));
    }
    public static void requireAccountTransport(Context context) {
        if (!accountsAllowed(context)) throw new IllegalStateException("账号服务要求 HTTPS/WSS；仅显式配置的本地调试允许 HTTP");
    }
    public static void setEndpoint(Context context, String value) {
        if (!debug(context)) throw new IllegalStateException("正式版服务器地址不可修改");
        endpoint(value);
        if (AccountManager.get(context).isBusy() || !OnlineSession.get().canChangeAccount())
            throw new IllegalStateException("请先结束在线活动或账户请求");
        if (!context.getSharedPreferences(PREFS,0).edit().putString(KEY_ENDPOINT,value).commit())
            throw new IllegalStateException("地址保存失败");
        OnlineSession.get().disconnect();
    }
    public static void selectMigrationEndpoint(Context context, String value) {
        if (!"wss".equals(endpoint(value).getScheme())) throw new IllegalArgumentException("迁移目标必须启用 WSS/HTTPS");
        SecurePrefs.putStrict(context,PREFS,"trustedMigrationEndpoint",value);
        if (!context.getSharedPreferences(PREFS,0).edit().remove(KEY_ENDPOINT).commit())
            throw new IllegalStateException("地址保存失败");
    }
    public static void restoreEndpoint(Context context, String value) {
        endpoint(value);
        SecurePrefs.putStrict(context,PREFS,"trustedMigrationEndpoint",null);
        if (debug(context)) {
            if (!context.getSharedPreferences(PREFS,0).edit().putString(KEY_ENDPOINT,value).commit())
                throw new IllegalStateException("地址恢复失败");
        } else if (!value.equals(DEFAULT_ENDPOINT)) {
            SecurePrefs.putStrict(context,PREFS,"trustedMigrationEndpoint",value);
        }
    }
    public static boolean hasOverride(Context context) {
        return debug(context) && context.getSharedPreferences(PREFS,0).contains(KEY_ENDPOINT);
    }
}
