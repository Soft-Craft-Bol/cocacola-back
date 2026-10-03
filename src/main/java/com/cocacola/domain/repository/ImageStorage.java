package com.cocacola.domain.repository;

import com.cocacola.domain.model.ImageUpload;
import com.cocacola.domain.model.StoredImage;

/** Puerto de almacenamiento de imagenes (implementado con Cloudinary). */
public interface ImageStorage {

    StoredImage upload(ImageUpload image, String folder);

    void delete(String publicId);
}
