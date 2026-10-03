package com.cocacola.application.response;

import com.cocacola.commons.enums.Role;
import com.cocacola.domain.model.User;

public record UserResponse(String id, String name, String email, Role role, boolean active) {

    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.isActive());
    }
}
