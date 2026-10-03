package com.cocacola.domain.model;

/** Imagen ya guardada: URL publica e identificador para poder eliminarla despues. */
public record StoredImage(String url, String publicId) {
}
