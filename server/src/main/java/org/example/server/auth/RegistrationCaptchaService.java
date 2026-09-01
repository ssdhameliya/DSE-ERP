package org.example.server.auth;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;import java.time.Instant;import java.util.Map;import java.util.UUID;import java.util.concurrent.ConcurrentHashMap;
@Service public class RegistrationCaptchaService{
 private final SecureRandom random=new SecureRandom(); private final Map<String,Challenge> challenges=new ConcurrentHashMap<>();
 public AuthDtos.CaptchaResponse issue(){cleanup();int a=2+random.nextInt(8),b=1+random.nextInt(9);String id=UUID.randomUUID().toString();challenges.put(id,new Challenge(String.valueOf(a+b),Instant.now().plusSeconds(300).getEpochSecond(),0));return new AuthDtos.CaptchaResponse(id,"What is "+a+" + "+b+"?","5 minutes");}
 public boolean verify(String id,String answer){cleanup();Challenge c=challenges.get(id);if(c==null)return false;if(c.attempts()>=5){challenges.remove(id);return false;}if(!c.answer().equals(answer==null?"":answer.trim())){challenges.put(id,new Challenge(c.answer(),c.expiresAt(),c.attempts()+1));return false;}challenges.remove(id);return true;}
 private void cleanup(){long now=Instant.now().getEpochSecond();challenges.entrySet().removeIf(e->e.getValue().expiresAt()<now);}
 private record Challenge(String answer,long expiresAt,int attempts){}
}
