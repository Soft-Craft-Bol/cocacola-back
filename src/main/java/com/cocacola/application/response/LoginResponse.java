package com.cocacola.application.response;

public record LoginResponse(String token, UserResponse user) {
}
