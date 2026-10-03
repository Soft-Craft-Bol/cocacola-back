package com.cocacola.domain.repository;

import com.cocacola.domain.model.User;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

    List<User> findAll();
    Optional<User> findById(String id);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    User save(User user);
    void deleteById(String id);
    long count();
}
