package com.memorygames.pk.user;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v2")
public class AccountController {
    private final AccountService accounts;
    public AccountController(AccountService accounts) { this.accounts=accounts; }
    public record Request(String installationId,String guestSecret,String nickname,String deviceName,String legacyToken,String email,String password,String verificationCode,String newPassword,String refreshToken,String avatarValue) {}
    private AccountService.Principal current(HttpServletRequest request) {
        String h=request.getHeader("Authorization");
        return accounts.authenticate(h!=null&&h.startsWith("Bearer ")?h.substring(7):null);
    }
    @PostMapping("/auth/guest") public Object guest(@RequestBody Request r) { return accounts.guest(r.installationId,r.guestSecret,r.nickname,r.deviceName); }
    @PostMapping("/auth/migrate") public Object migrate(@RequestBody Request r) { return accounts.migrate(r.legacyToken,r.installationId,r.guestSecret,r.deviceName); }
    @PostMapping("/auth/login") public Object login(@RequestBody Request r) { return accounts.login(r.email,r.password,r.installationId,r.deviceName); }
    @PostMapping("/auth/refresh") public Object refresh(@RequestBody Request r) { return accounts.refresh(r.refreshToken); }
    @PostMapping("/auth/logout") public Object logout(HttpServletRequest request) { accounts.logout(current(request),false); return Map.of("ok",true); }
    @PostMapping("/auth/logout-all") public Object logoutAll(HttpServletRequest request) { accounts.logout(current(request),true); return Map.of("ok",true); }
    @PostMapping("/account/email-verification") public Object verify(@RequestBody Request r,HttpServletRequest request) { accounts.sendVerification(current(request),r.email); return Map.of("ok",true); }
    @PostMapping("/auth/register") public Object register(@RequestBody Request r,HttpServletRequest request) { return accounts.register(current(request),r.email,r.password,r.verificationCode); }
    @PostMapping("/auth/password-reset/request") public Object resetRequest(@RequestBody Request r) { accounts.resetRequest(r.email); return Map.of("ok",true); }
    @PostMapping("/auth/password-reset/confirm") public Object resetConfirm(@RequestBody Request r) { accounts.resetConfirm(r.email,r.verificationCode,r.newPassword); return Map.of("ok",true); }
    @GetMapping("/me") public Object me(HttpServletRequest request) { return accounts.view(current(request).user()); }
    @GetMapping("/me/profile") public Object profileInfo(HttpServletRequest request) { return accounts.view(current(request).user()); }
    @PatchMapping("/me/profile") public Object profile(@RequestBody Request r,HttpServletRequest request) { return accounts.profile(current(request),r.nickname,r.avatarValue); }
    @GetMapping("/me/devices") public Object devices(HttpServletRequest request) { return accounts.devices(current(request)); }
    @DeleteMapping("/me/devices/{deviceId}") public Object remove(@PathVariable String deviceId,HttpServletRequest request) { accounts.removeDevice(current(request),deviceId); return Map.of("ok",true); }
    @PostMapping("/me/delete-request") public Object delete(@RequestBody(required=false) Request r,HttpServletRequest request) { accounts.deleteRequest(current(request),r==null?null:r.password); return Map.of("ok",true); }
    @PostMapping("/auth/delete-cancel") public Object cancel(@RequestBody Request r) { return accounts.cancelDelete(r.email,r.password,r.installationId,r.deviceName); }
}
