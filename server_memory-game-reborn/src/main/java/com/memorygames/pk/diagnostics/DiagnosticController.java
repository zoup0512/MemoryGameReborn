package com.memorygames.pk.diagnostics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorygames.pk.user.*;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

@RestController
@RequestMapping("/api/v2/diagnostics")
public class DiagnosticController {
    private static final Set<String> KEYS=Set.of("schemaVersion","uploadId","appVersion","buildTime","startedAt","endedAt","content","matchId");
    private final AccountService accounts;
    private final UserService users;
    private final DiagnosticService diagnostics;
    private final ObjectMapper json;
    public DiagnosticController(AccountService accounts,UserService users,DiagnosticService diagnostics,ObjectMapper json) {
        this.accounts=accounts; this.users=users; this.diagnostics=diagnostics; this.json=json;
    }
    private long authenticate(String authorization) {
        if(authorization==null || !authorization.startsWith("Bearer ")) throw new AccountException(401,"INVALID_CREDENTIALS","Authentication required");
        String token=authorization.substring(7);
        if(token.isBlank() || token.length()>512) throw new AccountException(401,"INVALID_CREDENTIALS","Authentication required");
        try { return accounts.authenticate(token).user().id; }
        catch(AccountException ex) {
            // Expired/revoked/banned v2 principals never fall back to legacy auth.
            if(!"INVALID_CREDENTIALS".equals(ex.code)) throw ex;
            UserAccount user=users.byToken(token).orElseThrow(()->ex);
            if(user.bannedUntil>System.currentTimeMillis()) throw new AccountException(403,"ACCOUNT_BANNED","Account banned");
            return user.id;
        }
    }
    @PostMapping("/uploads")
    public DiagnosticService.Receipt upload(@RequestHeader(value="Authorization",required=false) String authorization,@RequestBody JsonNode body) {
        long userId=authenticate(authorization);
        if(!body.isObject()) throw DiagnosticSanitizer.invalid();
        body.fieldNames().forEachRemaining(key->{ if(!KEYS.contains(key)) throw DiagnosticSanitizer.invalid(); });
        for(String key:Set.of("uploadId","appVersion","buildTime","content")) if(!body.path(key).isTextual()) throw DiagnosticSanitizer.invalid();
        for(String key:Set.of("schemaVersion","startedAt","endedAt")) if(!body.path(key).isIntegralNumber() || !body.path(key).canConvertToLong()) throw DiagnosticSanitizer.invalid();
        if(!body.path("schemaVersion").canConvertToInt() || body.hasNonNull("matchId") && !body.get("matchId").isTextual()) throw DiagnosticSanitizer.invalid();
        try { return diagnostics.upload(userId,json.treeToValue(body,DiagnosticService.Upload.class)); }
        catch(com.fasterxml.jackson.core.JsonProcessingException ex) { throw DiagnosticSanitizer.invalid(); }
    }
}
