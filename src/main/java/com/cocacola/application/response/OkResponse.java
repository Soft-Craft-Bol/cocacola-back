package com.cocacola.application.response;

public record OkResponse(boolean ok) {

    public static OkResponse done() {
        return new OkResponse(true);
    }
}
