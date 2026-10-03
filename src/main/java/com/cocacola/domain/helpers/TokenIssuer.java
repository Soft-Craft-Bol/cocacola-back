package com.cocacola.domain.helpers;

import com.cocacola.domain.model.User;

/** Puerto: emite el token de sesion. La implementacion (JWT) vive en utils. */
public interface TokenIssuer {

    String issue(User user);
}
