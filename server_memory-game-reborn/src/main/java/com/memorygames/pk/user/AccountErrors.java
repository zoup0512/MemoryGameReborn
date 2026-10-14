package com.memorygames.pk.user;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;

@RestControllerAdvice(assignableTypes=AccountController.class)
public class AccountErrors {
    @ExceptionHandler(AccountException.class) public ResponseEntity<?> account(AccountException ex) { return ResponseEntity.status(ex.status).body(Map.of("code",ex.code,"message",ex.getMessage())); }
    @ExceptionHandler(DataIntegrityViolationException.class) public ResponseEntity<?> conflict() { return ResponseEntity.status(409).body(Map.of("code","ACCOUNT_CONFLICT","message","Account identity already exists")); }
    @ExceptionHandler({HttpMessageNotReadableException.class,IllegalArgumentException.class}) public ResponseEntity<?> invalid() { return ResponseEntity.badRequest().body(Map.of("code","INVALID_INPUT","message","Invalid request")); }
}
