package com.cocacola.domain.model;

/** Archivo de imagen recibido para subir al almacenamiento. */
public record ImageUpload(byte[] content, String filename, String contentType) {
}
