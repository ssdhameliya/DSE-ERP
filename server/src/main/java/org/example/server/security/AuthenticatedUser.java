package org.example.server.security;

public record AuthenticatedUser(int id, String username, String role) {
}

