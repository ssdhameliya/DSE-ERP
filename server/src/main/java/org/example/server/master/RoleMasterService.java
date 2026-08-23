package org.example.server.master;

import org.example.server.persistence.JpaNativeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Canonical application-role catalog backed by normal Master Data (master_category + lookup_master).
 *
 * <p>For the ROLE category the business/security identity is the user-entered lookup_value.
 * lookup_code (for example ROL003) is only the generic Master Data technical identifier and is
 * deliberately ignored by authentication, user administration, permission assignment, MFA and
 * approval logic. Role values are matched case-insensitively and with surrounding whitespace ignored.</p>
 */
@Service
public class RoleMasterService {
    private static final String ROLE_CATEGORY_CODE = "ROLE";
    private final JpaNativeRepository jdbc;

    public RoleMasterService(JpaNativeRepository jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * code is retained in the shared/admin DTO contract for compatibility, but it is the normalized
     * ROLE lookup_value identity (not lookup_master.lookup_code). displayName preserves user casing.
     */
    public record RoleDefinition(int id, String code, String displayName, String description,
                                 boolean active, int displayOrder, long userCount) {}

    @Transactional(readOnly = true)
    public List<RoleDefinition> activeRoles() {
        return roles(true);
    }

    @Transactional(readOnly = true)
    public List<RoleDefinition> roles(boolean activeOnly) {
        String activeClause = activeOnly ? " AND COALESCE(lm.is_active,1)=1 AND COALESCE(mc.is_active,1)=1" : "";
        return jdbc.query("""
                SELECT lm.id,
                       UPPER(TRIM(lm.lookup_value)) AS role_identity,
                       TRIM(lm.lookup_value) AS display_name,
                       COALESCE(lm.description,''),
                       COALESCE(lm.is_active,1),
                       COALESCE(lm.display_order,0),
                       (SELECT COUNT(*) FROM users u WHERE UPPER(TRIM(COALESCE(u.role,'')))=UPPER(TRIM(lm.lookup_value))) AS user_count
                  FROM lookup_master lm
                  JOIN master_category mc ON UPPER(TRIM(mc.category_name))=UPPER(TRIM(lm.lookup_type))
                 WHERE UPPER(TRIM(mc.category_code))=?
                   AND TRIM(COALESCE(lm.lookup_value,''))<>''
                """ + activeClause + " ORDER BY COALESCE(lm.display_order,0), UPPER(TRIM(lm.lookup_value)), lm.id",
                (row, index) -> new RoleDefinition(row.getInt(1), row.getString(2), row.getString(3), row.getString(4),
                        flag(row.getObject(5)), row.getInt(6), row.getLong(7)), ROLE_CATEGORY_CODE);
    }

    @Transactional(readOnly = true)
    public RoleDefinition requireActive(String value) {
        String candidate = normalize(value);
        if (candidate.isBlank()) throw new IllegalArgumentException("Role is required");
        List<RoleDefinition> matches = jdbc.query("""
                SELECT lm.id,
                       UPPER(TRIM(lm.lookup_value)) AS role_identity,
                       TRIM(lm.lookup_value) AS display_name,
                       COALESCE(lm.description,''),
                       COALESCE(lm.is_active,1),
                       COALESCE(lm.display_order,0),
                       (SELECT COUNT(*) FROM users u WHERE UPPER(TRIM(COALESCE(u.role,'')))=UPPER(TRIM(lm.lookup_value))) AS user_count
                  FROM lookup_master lm
                  JOIN master_category mc ON UPPER(TRIM(mc.category_name))=UPPER(TRIM(lm.lookup_type))
                 WHERE UPPER(TRIM(mc.category_code))=?
                   AND COALESCE(mc.is_active,1)=1
                   AND COALESCE(lm.is_active,1)=1
                   AND UPPER(TRIM(lm.lookup_value))=?
                 ORDER BY lm.id
                 LIMIT 1
                """, (row, index) -> new RoleDefinition(row.getInt(1), row.getString(2), row.getString(3), row.getString(4),
                        flag(row.getObject(5)), row.getInt(6), row.getLong(7)), ROLE_CATEGORY_CODE, candidate);
        if (matches.isEmpty()) throw new IllegalArgumentException("Role is not active in Role Master: " + (value == null ? "" : value.trim()));
        return matches.getFirst();
    }

    @Transactional(readOnly = true)
    public boolean isActive(String value) {
        try { requireActive(value); return true; }
        catch (IllegalArgumentException ignored) { return false; }
    }

    /** Normalized, case-insensitive identity derived exclusively from lookup_value. */
    public String code(String value) { return requireActive(value).code(); }
    public String displayName(String value) { return requireActive(value).displayName(); }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean flag(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean bool) return bool;
        String text = String.valueOf(value);
        return "1".equals(text) || "t".equalsIgnoreCase(text) || "true".equalsIgnoreCase(text);
    }
}
