package org.son.haruharu.domain.mission.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.mission.implement.UploadSlotRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class UploadSlotRepositoryImpl implements UploadSlotRepository {

    private final UploadSlotJpaRepository jpaRepository;

    @Override
    public List<UploadSlot> findAll() {
        return jpaRepository.findAll().stream().map(UploadSlotEntity::toDomain).toList();
    }

    @Override
    public List<UploadSlot> findByDayNumber(int dayNumber) {
        return jpaRepository.findByDayNumberOrderBySlotIndexAsc(dayNumber).stream()
                .map(UploadSlotEntity::toDomain).toList();
    }

    @Override
    public List<UploadSlot> saveAll(List<UploadSlot> slots) {
        return jpaRepository.saveAll(slots.stream().map(UploadSlotEntity::from).toList()).stream()
                .map(UploadSlotEntity::toDomain)
                .toList();
    }
}
