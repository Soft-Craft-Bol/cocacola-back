package com.cocacola.domain.helpers;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String what) {
        super(what + " no encontrado");
    }
}
