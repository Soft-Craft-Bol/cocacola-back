package com.cocacola.persistence.crud;

import com.cocacola.persistence.entity.ParticipantEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantCrudRepository extends JpaRepository<ParticipantEntity, String> {
    @org.springframework.data.jpa.repository.Query("""
        select p from ParticipantEntity p
        where p.eventId = :eventId and (:search = '' or
          lower(concat(coalesce(p.firstName, ''), ' ', coalesce(p.lastName, ''), ' ',
            coalesce(p.email, ''), ' ', coalesce(p.phone, ''), ' ', coalesce(p.qrCode, '')))
          like :search escape '!')
        """)
    org.springframework.data.domain.Page<ParticipantEntity> search(
        @org.springframework.data.repository.query.Param("eventId") String eventId,
        @org.springframework.data.repository.query.Param("search") String search,
        org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "preferences")
    List<ParticipantEntity> findByIdIn(List<String> ids);

    List<ParticipantEntity> findByEmailIgnoreCase(String email);

    @org.springframework.data.jpa.repository.Query("""
        select p from ParticipantEntity p
        where replace(replace(replace(replace(replace(coalesce(p.phone, ''), ' ', ''), '-', ''), '+', ''), '(', ''), ')', '')
          like concat('%', :digits)
        """)
    List<ParticipantEntity> findByPhoneDigits(@org.springframework.data.repository.query.Param("digits") String digits);

    List<ParticipantEntity> findByEventId(String eventId);
    Optional<ParticipantEntity> findByQrCode(String qrCode);
    boolean existsByEventIdAndEmail(String eventId, String email);
    boolean existsByEmail(String email);
    boolean existsByQrCode(String qrCode);
    void deleteByEventId(String eventId);
}
