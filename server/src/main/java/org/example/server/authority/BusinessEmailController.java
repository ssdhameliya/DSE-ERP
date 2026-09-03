package org.example.server.authority;

import org.example.server.auth.SmtpMailService;
import org.example.server.security.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;

@RestController
@RequestMapping("/api/authority/email")
public class BusinessEmailController {
    private final SmtpMailService mail;
    public BusinessEmailController(SmtpMailService mail) { this.mail = mail; }

    @PostMapping
    public Result send(@RequestBody Request r) {
        CurrentUser.requirePermission("COMMUNICATION.CREATE", "Send business email");
        return sendInternal(r, "Sent by company server");
    }

    @PostMapping("/resend")
    public Result resend(@RequestBody Request r) {
        CurrentUser.requirePermission("COMMUNICATION.RESEND", "Re-send business email");
        return sendInternal(r, "Re-sent by company server");
    }

    private Result sendInternal(Request r, String message) {
        byte[] attachment = r.attachmentBase64() == null || r.attachmentBase64().isBlank()
                ? null : Base64.getDecoder().decode(r.attachmentBase64());
        mail.sendBusiness(r.recipient(), r.subject(), r.body(), r.attachmentName(), attachment);
        return new Result(true, message);
    }

    @GetMapping("/settings")
    public Settings settings() {
        var current = mail.currentSettings();
        return new Settings(current.email(), current.password(), current.host(), current.port());
    }

    @PutMapping("/settings")
    public Settings settings(@RequestBody Settings requested) {
        var saved = mail.saveSettings(requested.email(), requested.appPassword(), requested.host(), requested.port());
        return new Settings(saved.email(), saved.password(), saved.host(), saved.port());
    }

    @PostMapping("/test")
    public Result test(@RequestBody TestRequest request) {
        String recipient = request == null ? null : request.recipient();
        mail.sendBusiness(recipient, "DSE ERP email test", "Your DSE ERP company-server email configuration is working correctly.", null, null);
        return new Result(true, "Test email sent successfully");
    }

    public record Request(String recipient, String subject, String body, String attachmentName, String attachmentBase64) {}
    public record Settings(String email, String appPassword, String host, Integer port) {}
    public record TestRequest(String recipient) {}
    public record Result(boolean success, String message) {}
}
