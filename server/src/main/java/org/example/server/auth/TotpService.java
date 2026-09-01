package org.example.server.auth;

import org.example.shared.SecretValueCodec;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TotpService {
    private static final String ALPHABET="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM=new SecureRandom();
    private final Map<String, LoginChallenge> logins=new ConcurrentHashMap<>();

    public Setup createSetup(String username, String email){
        byte[] raw=new byte[20]; RANDOM.nextBytes(raw); String secret=base32(raw);
        String account=(email==null||email.isBlank()?username:email).trim();
        String uri="otpauth://totp/"+enc("DSE ERP:"+account)+"?secret="+secret+"&issuer="+enc("DSE ERP")+"&algorithm=SHA1&digits=6&period=30";
        return new Setup(secret, SecretValueCodec.encrypt(secret), uri);
    }
    public String decrypt(String encrypted){return SecretValueCodec.decrypt(encrypted);}
    public boolean verifyEncrypted(String encrypted,String code){return verify(decrypt(encrypted),code);}
    public boolean verify(String secret,String code){
        if(secret==null||secret.isBlank()||code==null||!code.trim().matches("\\d{6}"))return false;
        long step=Instant.now().getEpochSecond()/30L;
        for(long offset=-1;offset<=1;offset++) if(code.trim().equals(generate(secret,step+offset))) return true;
        return false;
    }
    public String issueLogin(int userId){cleanup();String id=UUID.randomUUID().toString();logins.put(id,new LoginChallenge(userId,Instant.now().plusSeconds(300).getEpochSecond()));return id;}
    public Integer consumeLogin(String id){cleanup();LoginChallenge c=logins.remove(id);return c==null?null:c.userId();}
    public Integer peekLogin(String id){cleanup();LoginChallenge c=logins.get(id);return c==null?null:c.userId();}
    private void cleanup(){long now=Instant.now().getEpochSecond();logins.entrySet().removeIf(e->e.getValue().expiresAt()<now);}
    private String generate(String secret,long counter){try{byte[] key=decode32(secret);byte[] msg=new byte[8];for(int i=7;i>=0;i--){msg[i]=(byte)(counter&0xff);counter>>>=8;}Mac mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(key,"HmacSHA1"));byte[] h=mac.doFinal(msg);int o=h[h.length-1]&15;int bin=((h[o]&127)<<24)|((h[o+1]&255)<<16)|((h[o+2]&255)<<8)|(h[o+3]&255);return String.format(Locale.ROOT,"%06d",bin%1_000_000);}catch(Exception e){throw new IllegalStateException("Unable to verify authenticator code",e);}}
    private static String base32(byte[] bytes){StringBuilder out=new StringBuilder();int buffer=0,bits=0;for(byte b:bytes){buffer=(buffer<<8)|(b&255);bits+=8;while(bits>=5){out.append(ALPHABET.charAt((buffer>>(bits-5))&31));bits-=5;}}if(bits>0)out.append(ALPHABET.charAt((buffer<<(5-bits))&31));return out.toString();}
    private static byte[] decode32(String text){String s=text.replace("=","").replace(" ","").toUpperCase(Locale.ROOT);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();int buffer=0,bits=0;for(char c:s.toCharArray()){int v=ALPHABET.indexOf(c);if(v<0)throw new IllegalArgumentException("Invalid authenticator secret");buffer=(buffer<<5)|v;bits+=5;if(bits>=8){out.write((buffer>>(bits-8))&255);bits-=8;}}return out.toByteArray();}
    private static String enc(String v){return URLEncoder.encode(v, StandardCharsets.UTF_8).replace("+","%20");}
    public record Setup(String manualSecret,String encryptedSecret,String provisioningUri){}
    private record LoginChallenge(int userId,long expiresAt){}
}
