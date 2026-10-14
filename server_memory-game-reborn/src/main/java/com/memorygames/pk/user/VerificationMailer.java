package com.memorygames.pk.user;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class VerificationMailer {
    private final ObjectProvider<JavaMailSender> sender;
    private final String from;
    public VerificationMailer(ObjectProvider<JavaMailSender> sender, @Value("${pk.auth.mail-from:}") String from) {
        this.sender=sender; this.from=from;
    }
    public void checkAvailable() {
        if(sender.getIfAvailable()==null || from.isBlank()) throw new AccountException(503,"MAIL_UNAVAILABLE","Email service is not configured");
    }
    public void send(String email, String code, String purpose) {
        checkAvailable();
        JavaMailSender mail=sender.getIfAvailable();
        SimpleMailMessage message=new SimpleMailMessage();
        message.setFrom(from); message.setTo(email); message.setSubject("Memory Game verification");
        message.setText("Your "+purpose+" code is "+code+". It expires in 10 minutes.");
        try { mail.send(message); }
        catch (Exception ignored) { throw new AccountException(503,"MAIL_UNAVAILABLE","Email service is unavailable"); }
    }
}
