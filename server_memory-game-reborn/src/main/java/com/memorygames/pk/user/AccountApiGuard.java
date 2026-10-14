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
        response.setHeader("Cache-Control","no-store");
        if(!allowHttp&&!request.isSecure()) { fail(response,403,"HTTPS_REQUIRED"); return; }
        String path=request.getRequestURI();
        boolean upload="POST".equals(request.getMethod()) && "/api/v2/diagnostics/uploads".equals(path);
        int maxBytes=upload?256*1024:16384;
        if(upload) {
            String encoding=request.getHeader("Content-Encoding");
            if(encoding!=null && !encoding.isBlank() && !"identity".equalsIgnoreCase(encoding.trim())) { fail(response,415,"UNSUPPORTED_MEDIA_TYPE"); return; }
            try {
                org.springframework.http.MediaType type=org.springframework.http.MediaType.parseMediaType(request.getContentType());
                if(!"application".equalsIgnoreCase(type.getType()) || !"json".equalsIgnoreCase(type.getSubtype()) ||
                        (type.getCharset()!=null && !java.nio.charset.StandardCharsets.UTF_8.equals(type.getCharset()))) { fail(response,415,"UNSUPPORTED_MEDIA_TYPE"); return; }
            } catch(IllegalArgumentException ex) { fail(response,415,"UNSUPPORTED_MEDIA_TYPE"); return; }
        }
        if(request.getContentLengthLong()>maxBytes) { fail(response,upload?413:400,upload?"PAYLOAD_TOO_LARGE":"INVALID_INPUT"); return; }
        int limit=path.contains("email-verification")||path.contains("password-reset/request")?5:path.contains("/auth/guest")?20:path.contains("/auth/login")||path.contains("/auth/delete-cancel")?10:120;
        if(!acquire(ip+":"+path,limit)||!acquire(ip+":all",300)) { fail(response,429,"RATE_LIMITED"); return; }
        // Bound the actual stream even without Content-Length, and for non-JSON v2 requests.
        {
            byte[] bytes=request.getInputStream().readNBytes(maxBytes+1);
            if(bytes.length>maxBytes) { fail(response,upload?413:400,upload?"PAYLOAD_TOO_LARGE":"INVALID_INPUT"); return; }
            if(upload) {
                try { java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(bytes)); }
                catch(java.nio.charset.CharacterCodingException ex) { fail(response,400,"INVALID_INPUT"); return; }
            }
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
        }
    }
}
