package org.example.server.audit;

import org.example.server.persistence.JpaNativeRepository;
import org.example.server.security.CurrentUser;
import org.example.server.util.BusinessClock;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final JpaNativeRepository jdbc;

    public AuditService(JpaNativeRepository jdbc) {
        this.jdbc = jdbc;
    }

    public void log(String entityType, Number entityId, String action, String detail) {
        if (entityId == null) return;
        jdbc.update(
            "INSERT INTO activity_log(entity_type,entity_id,action,detail,created_by,created_at) VALUES(?,?,?,?,?,?)",
            normalize(entityType), entityId.longValue(), normalize(action), safe(detail),
            CurrentUser.require().username(), BusinessClock.nowUtcText()
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private static String safe(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() <= 1000 ? text : text.substring(0, 1000);
    }
}
