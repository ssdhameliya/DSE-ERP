package org.example.service;

import org.example.dao.UserDAO;
import org.example.model.AppUser;

public class UserService {

    private final UserDAO dao = new UserDAO();

    public AppUser authenticate(String identity, String password) {
        return dao.authenticate(identity, password);
    }

    public void register(AppUser user) {
        dao.register(user);
    }

    public void changePassword(int id, String password) {
        dao.changePassword(id, password);
    }
}
