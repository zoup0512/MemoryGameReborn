package com.cube.memorygames.api.online;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Blocking transport, used exclusively by AccountManager's worker; never follows redirects with credentials. */
final class AccountRestClient {
    private final Gson gson = new Gson();
    JsonObject request(String base, String method, String path, JsonObject body, String token) throws Exception {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
            throw new IllegalStateException("账号网络请求不能在主线程执行");
        if ("/diagnostics/uploads".equals(path)) {
            com.cube.memorygames.diagnostics.DiagnosticUploadPolicy.requireHttps(base);
            if (body == null || gson.toJson(body).getBytes("UTF-8").length > 256*1024)
                throw new IllegalStateException("诊断请求过大");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(10000); connection.setReadTimeout(15000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (body != null) {
                byte[] data = gson.toJson(body).getBytes("UTF-8");
                connection.setDoOutput(true); connection.setFixedLengthStreamingMode(data.length);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream out = connection.getOutputStream()) { out.write(data); }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String text = "";
            if (stream != null) {
                try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096]; int count;
                    while ((count = in.read(buffer)) != -1) {
                        if (out.size() + count > 1024 * 1024) throw new IllegalStateException("响应过大");
                        out.write(buffer, 0, count);
                    }
                    text = new String(out.toByteArray(), "UTF-8");
                }
            }
            JsonObject result;
            try { result = text.isEmpty() ? new JsonObject() : new JsonParser().parse(text).getAsJsonObject(); }
            catch (RuntimeException e) { throw new AccountManager.AccountError(status, "INVALID_RESPONSE", "服务响应格式错误"); }
            if (status < 200 || status >= 300) {
                String code = result.has("code") ? result.get("code").getAsString() : "HTTP_" + status;
                String message = result.has("message") ? result.get("message").getAsString() : "服务请求失败";
                throw new AccountManager.AccountError(status, code, message);
            }
            return result;
        } finally { connection.disconnect(); }
    }
}
