package com.cocacola.persistence.crud;

import com.cocacola.domain.model.User;
import com.cocacola.domain.repository.UserRepository;
import com.cocacola.persistence.mapper.UserMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserCrudRepository crud;
    private final UserMapper mapper;

    @Override
    public List<User> findAll() {
        return crud.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<User> findById(String id) {
        return crud.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return crud.findByEmailIgnoreCase(email).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return crud.existsByEmailIgnoreCase(email);
    }

    @Override
    public User save(User user) {
        return mapper.toDomain(crud.save(mapper.toEntity(user)));
    }

    @Override
    public void deleteById(String id) {
        crud.deleteById(id);
    }

    @Override
    public long count() {
        return crud.count();
    }
}
