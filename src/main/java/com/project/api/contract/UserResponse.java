package com.project.api.contract;

import java.time.Instant;

import com.project.api.enums.Role;
import com.project.api.model.User;

public record UserResponse(
        Long id,
        String username,
        Role role,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.getCreatedAt());
    }
}
