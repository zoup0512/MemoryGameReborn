package com.memorygames.pk.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AccountApiGuard extends OncePerRequestFilter {
    private final boolean allowHttp;
    private final ObjectMapper json;
    private final ConcurrentHashMap<String,Bucket> buckets=new ConcurrentHashMap<>();
    private record Bucket(long start,int count) {}
    public AccountApiGuard(@Value("${pk.auth.allow-http:false}") boolean allowHttp,ObjectMapper json) { this.allowHttp=allowHttp; this.json=json; }
    private boolean acquire(String key,int limit) {
        long now=System.currentTimeMillis();
        if(buckets.size()>20000) buckets.entrySet().removeIf(e->now-e.getValue().start>3600000);
        // Fail closed instead of resetting counters when an attacker fills the map.
        if(!buckets.containsKey(key)&&buckets.size()>20000) return false;
        return buckets.compute(key,(k,b)->b==null||now-b.start>=60000?new Bucket(now,1):new Bucket(b.start,b.count+1)).count<=limit;
    }
    private void fail(HttpServletResponse response,int status,String code) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); json.writeValue(response.getOutputStream(),Map.of("code",code,"message",code.replace('_',' ')));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        if(!request.getRequestURI().startsWith("/api/v2/")) { chain.doFilter(request,response); return; }
        String ip=request.getRemoteAddr(); // Never parse untrusted forwarding headers.
        if(!allowHttp&&!request.isSecure()) { fail(response,403,"HTTPS_REQUIRED"); return; }
        if(request.getContentLengthLong()>16384) { fail(response,400,"INVALID_INPUT"); return; }
        response.setHeader("Cache-Control","no-store");
        String path=request.getRequestURI();
        int limit=path.contains("email-verification")||path.contains("password-reset/request")?5:path.contains("/auth/guest")?20:path.contains("/auth/login")||path.contains("/auth/delete-cancel")?10:120;
        if(!acquire(ip+":"+path,limit)||!acquire(ip+":all",300)) { fail(response,429,"RATE_LIMITED"); return; }
        if (request.getContentType()!=null && request.getContentType().startsWith("application/json")) {
            byte[] bytes=request.getInputStream().readNBytes(16385);
            if(bytes.length>16384) { fail(response,400,"INVALID_INPUT"); return; }
            jakarta.servlet.http.HttpServletRequestWrapper bounded=new jakarta.servlet.http.HttpServletRequestWrapper(request) {
                @Override public jakarta.servlet.ServletInputStream getInputStream() {
                    java.io.ByteArrayInputStream input=new java.io.ByteArrayInputStream(bytes);
                    return new jakarta.servlet.ServletInputStream() {
                        public int read() { return input.read(); }
                        public boolean isFinished() { return input.available()==0; }
                        public boolean isReady() { return true; }
                        public void setReadListener(jakarta.servlet.ReadListener listener) { throw new UnsupportedOperationException(); }
                    };
                }
                @Override public java.io.BufferedReader getReader() { return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8)); }
            };
            chain.doFilter(bounded,response);
        } else chain.doFilter(request,response);
    }
}
