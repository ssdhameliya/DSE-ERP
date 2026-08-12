package org.example.server.setup;

import org.example.server.persistence.JpaNativeRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SetupService {
    private final JpaNativeRepository jdbc;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    public SetupService(JpaNativeRepository jdbc){ this.jdbc=jdbc; }

    @Transactional
    public SetupDtos.BootstrapResponse bootstrap(SetupDtos.BootstrapRequest r){
        if(r==null || blank(r.companyName()) || blank(r.adminUsername()) || r.adminPassword()==null || r.adminPassword().length()<6)
            throw new IllegalArgumentException("Company, administrator username and a 6+ character password are required");
        Integer roleId=jdbc.queryForObject("SELECT id FROM roles WHERE role_name='ADMIN'",Integer.class);
        jdbc.update("INSERT INTO users(username,password,full_name,role,role_id,email,active,access_level,locked,failed_attempts,mfa_enabled) VALUES(?,?,?,?,?,?,1,'ADMIN',0,0,0) ON CONFLICT (username) DO UPDATE SET password=EXCLUDED.password,full_name=EXCLUDED.full_name,role='ADMIN',role_id=EXCLUDED.role_id,email=EXCLUDED.email,active=1,locked=0",
                r.adminUsername().trim(), passwords.encode(r.adminPassword()), nz(r.adminName()), "ADMIN", roleId, nz(r.adminEmail()));
        setting("company.name",r.companyName()); setting("company.phone",r.phone()); setting("company.email",r.companyEmail());
        setting("company.gstin",r.gstin()); setting("company.address",r.address()); setting("setup.completed","true");
        return new SetupDtos.BootstrapResponse(true,"READY");
    }
    private void setting(String k,String v){ jdbc.update("INSERT INTO application_setting(setting_key,setting_value) VALUES(?,?) ON CONFLICT (setting_key) DO UPDATE SET setting_value=EXCLUDED.setting_value",k,nz(v)); }
    private static boolean blank(String v){return v==null||v.isBlank();} private static String nz(String v){return v==null?"":v.trim();}
}
