package org.example.service;

import org.example.api.auth.AuthApiClient;
import org.example.config.ConfigManager;
import org.example.dao.UserDAO;
import org.example.model.AppUser;

/**
 * Authentication facade used by the JavaFX controllers.
 *
 * <p>When {@code auth.mode=api} (the default), login/reset related operations
 * are performed through the Spring Boot server.  {@code auth.mode=legacy}
 * remains available during the phased migration so the existing desktop can
 * be verified without a big-bang cutover.</p>
 */
public class UserService {

    private final UserDAO legacyDao = new UserDAO();
    private final AuthApiClient authApi = new AuthApiClient();

    public AppUser authenticate(String identity, String password) {
        return ConfigManager.isApiAuthenticationEnabled()
                ? authApi.authenticate(identity, password)
                : legacyDao.authenticate(identity, password);
    }

    public AppUser findActiveByIdentity(String identity) {
        return ConfigManager.isApiAuthenticationEnabled()
                ? authApi.findActiveByIdentity(identity)
                : legacyDao.findActiveByIdentity(identity);
    }

    public void recordSuccessfulLogin(int id) {
        if (ConfigManager.isApiAuthenticationEnabled()) authApi.recordSuccessfulLogin(id);
        else legacyDao.recordSuccessfulLogin(id);
    }

    public void register(AppUser user) {
        // Registration is intentionally left on the existing path until the
        // master-data/auth administration API phase is migrated.
        legacyDao.register(user);
    }

    public void changePassword(int id, String password) {
        if (ConfigManager.isApiAuthenticationEnabled()) authApi.changePassword(id, password);
        else legacyDao.changePassword(id, password);
    }
}
