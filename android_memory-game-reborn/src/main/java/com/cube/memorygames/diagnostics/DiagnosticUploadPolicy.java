package com.cube.memorygames.diagnostics;

import java.net.URI;
import java.util.Locale;

/** No generic insecure-origin upgrade; the historical legacy server has one pinned TLS destination. */
public final class DiagnosticUploadPolicy {
    public static final String LEGACY="ws://124.220.35.87:8091/ws";
    public static final String TRUSTED_BASE="https://www.razor123.site/api/v2";
    public static final String PATH="/diagnostics/uploads";
    private DiagnosticUploadPolicy() { }
    public static String destination(String endpoint,boolean v2) {
        try {
            URI uri=new URI(endpoint);
            if(uri.getHost()==null || uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null || !"/ws".equals(uri.getPath())) throw new IllegalArgumentException();
            if("wss".equals(uri.getScheme())) return new URI("https",null,uri.getHost().toLowerCase(Locale.US),uri.getPort(),"/api/v2",null,null).toString();
            if(!v2 && "ws".equals(uri.getScheme()) && "124.220.35.87".equals(uri.getHost()) && uri.getPort()==8091) return TRUSTED_BASE;
        } catch(Exception ignored) { }
        throw new IllegalArgumentException("诊断上传仅允许 HTTPS；此服务器没有可信的 HTTPS 诊断地址");
    }
    public static void requireHttps(String base) {
        try {
            URI uri=new URI(base);
            if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null || !"/api/v2".equals(uri.getPath())) throw new IllegalArgumentException();
        } catch(Exception e) { throw new IllegalArgumentException("诊断上传必须使用 HTTPS"); }
    }
}
