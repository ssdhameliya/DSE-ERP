package org.example.server.setup;

import org.example.server.master.RoleMasterService;
import org.example.server.persistence.JpaNativeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SetupService {
    private final JpaNativeRepository jdbc;
    private final PasswordEncoder passwords;
    private final RoleMasterService roles;
    public SetupService(JpaNativeRepository jdbc, PasswordEncoder passwords, RoleMasterService roles){ this.jdbc=jdbc; this.passwords=passwords; this.roles=roles; }

    @Transactional(readOnly = true)
    public SetupDtos.SetupStatus status(){
        Long users=jdbc.queryForObject("SELECT COUNT(*) FROM users",Long.class);
        Long admins=jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE UPPER(role)='ADMIN' AND active=1",Long.class);
        long userCount=users==null?0:users, adminCount=admins==null?0:admins;
        return new SetupDtos.SetupStatus(userCount==0 || adminCount==0,userCount,adminCount);
    }

    @Transactional
    public SetupDtos.BootstrapResponse bootstrap(SetupDtos.BootstrapRequest r){
        if(r==null || blank(r.companyName()) || blank(r.adminUsername()) || r.adminPassword()==null || r.adminPassword().length()<8
                || !r.adminPassword().matches(".*[A-Za-z].*") || !r.adminPassword().matches(".*[0-9].*"))
            throw new IllegalArgumentException("Company, administrator username and an 8+ character password with a letter and number are required");
        roles.requireActive("ADMIN");
        Long userCount=jdbc.queryForObject("SELECT COUNT(*) FROM users",Long.class);
        if(userCount!=null && userCount>0) throw new IllegalStateException("Initial setup has already been completed");
        jdbc.update("INSERT INTO users(username,password,full_name,role,role_id,email,active,access_level,locked,failed_attempts,mfa_enabled) VALUES(?,?,?,?,NULL,?,1,'ADMIN',0,0,0)",
                r.adminUsername().trim(), passwords.encode(r.adminPassword()), nz(r.adminName()), "ADMIN", nz(r.adminEmail()));
        setting("company.name",r.companyName()); setting("company.phone",r.phone()); setting("company.email",r.companyEmail());
        setting("company.gstin",r.gstin()); setting("company.address",r.address()); setting("setup.completed","true");
        return new SetupDtos.BootstrapResponse(true,"READY");
    }
    private void setting(String k,String v){ jdbc.update("INSERT INTO application_setting(setting_key,setting_value) VALUES(?,?) ON CONFLICT (setting_key) DO UPDATE SET setting_value=EXCLUDED.setting_value",k,nz(v)); }
    private static boolean blank(String v){return v==null||v.isBlank();} private static String nz(String v){return v==null?"":v.trim();}
}
