package com.project.api.model;

import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;

import com.project.api.enums.Role;

public record CurrentUser(String username, boolean admin) {

    public static final String ROLES_CLAIM = "roles";

    public static CurrentUser from(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        return new CurrentUser(jwt.getSubject(), roles != null && roles.contains(Role.ADMIN.name()));
    }
}
