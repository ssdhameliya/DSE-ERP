package org.example.server.authority;
import org.example.server.auth.SmtpMailService;
import org.springframework.web.bind.annotation.*;
import java.util.Base64;
@RestController @RequestMapping("/api/authority/email") public class BusinessEmailController{
 private final SmtpMailService mail;public BusinessEmailController(SmtpMailService mail){this.mail=mail;}
 @PostMapping public Result send(@RequestBody Request r){byte[] attachment=r.attachmentBase64()==null||r.attachmentBase64().isBlank()?null:Base64.getDecoder().decode(r.attachmentBase64());mail.sendBusiness(r.recipient(),r.subject(),r.body(),r.attachmentName(),attachment);return new Result(true,"Sent by company server");}
 public record Request(String recipient,String subject,String body,String attachmentName,String attachmentBase64){} public record Result(boolean success,String message){}
}
