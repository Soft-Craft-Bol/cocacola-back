package com.cocacola.persistence.crud;

import com.cocacola.domain.model.Participant;
import com.cocacola.domain.repository.ParticipantRepository;
import com.cocacola.persistence.mapper.ParticipantMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
@RequiredArgsConstructor
public class ParticipantRepositoryImpl implements ParticipantRepository {

    private final ParticipantCrudRepository crud;
    private final ParticipantMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Participant> search(String eventId, String search, int page, int size) {
        String term = search == null ? "" : search.trim().toLowerCase(java.util.Locale.ROOT);
        String pattern = term.isEmpty() ? "" : "%" + term.replace("!", "!!").replace("%", "!%")
                .replace("_", "!_") + "%";
        var result = crud.search(eventId, pattern, org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("registeredAt").descending()
                        .and(org.springframework.data.domain.Sort.by("id"))));
        // Fetch collections only for the selected page, without paginating a collection join.
        if (!result.isEmpty()) {
            crud.findByIdIn(result.getContent().stream().map(p -> p.getId()).toList());
        }
        return result.map(mapper::toDomain);
    }

    @Override
    public List<Participant> findAll() {
        return crud.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Participant> findByEventId(String eventId) {
        return crud.findByEventId(eventId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Participant> findById(String id) {
        return crud.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Participant> findByQrCode(String qrCode) {
        return crud.findByQrCode(qrCode).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEventIdAndEmail(String eventId, String email) {
        return crud.existsByEventIdAndEmail(eventId, email);
    }

    @Override
    public boolean existsByEmail(String email) {
        return crud.existsByEmail(email);
    }

    @Override
    public boolean existsByQrCode(String qrCode) {
        return crud.existsByQrCode(qrCode);
    }

    @Override
    public Participant save(Participant participant) {
        return mapper.toDomain(crud.save(mapper.toEntity(participant)));
    }

    @Override
    public List<Participant> saveAll(List<Participant> items) {
        return crud.saveAll(items.stream().map(mapper::toEntity).toList()).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteById(String id) {
        crud.deleteById(id);
    }

    @Override
    public void deleteByEventId(String eventId) {
        crud.deleteByEventId(eventId);
    }
}
