package com.cocacola.persistence.crud;

import com.cocacola.domain.model.Notification;
import com.cocacola.domain.repository.NotificationRepository;
import com.cocacola.persistence.mapper.NotificationMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationCrudRepository crud;
    private final NotificationMapper mapper;

    @Override
    public Notification save(Notification notification) {
        return mapper.toDomain(crud.save(mapper.toEntity(notification)));
    }

    @Override
    public Optional<Notification> findById(String id) {
        return crud.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Notification> findRecent(boolean unreadOnly, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, 100)));
        return (unreadOnly ? crud.findByReadFalseOrderByCreatedAtDesc(page) : crud.findAllByOrderByCreatedAtDesc(page))
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public long countUnread() {
        return crud.countByReadFalse();
    }

    @Override
    public void markAllRead() {
        crud.markAllRead();
    }
}
