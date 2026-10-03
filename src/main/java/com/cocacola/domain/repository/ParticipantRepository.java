package com.cocacola.domain.repository;

import com.cocacola.domain.model.Participant;
import java.util.List;
import java.util.Optional;

public interface ParticipantRepository {

    List<Participant> findAll();
    org.springframework.data.domain.Page<Participant> search(String eventId, String search, int page, int size);
    List<Participant> findByEventId(String eventId);
    Optional<Participant> findById(String id);
    Optional<Participant> findByQrCode(String qrCode);
    boolean existsByEventIdAndEmail(String eventId, String email);
    boolean existsByEmail(String email);
    boolean existsByQrCode(String qrCode);
    Participant save(Participant participant);

    List<Participant> saveAll(List<Participant> items);
    void deleteById(String id);
    void deleteByEventId(String eventId);
}
