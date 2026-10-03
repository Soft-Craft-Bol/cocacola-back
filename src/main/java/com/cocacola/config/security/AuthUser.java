package com.cocacola.config.security;

/** Usuario autenticado extraido del JWT (principal de Spring Security). */
public record AuthUser(String id, String email, String role) {
}
