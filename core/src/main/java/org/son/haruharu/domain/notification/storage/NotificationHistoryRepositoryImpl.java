package org.son.haruharu.domain.notification.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.notification.implement.NotificationHistory;
import org.son.haruharu.domain.notification.implement.NotificationHistoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
@RequiredArgsConstructor
public class NotificationHistoryRepositoryImpl implements NotificationHistoryRepository {

    // payload는 Map<String,String>이라 직렬화에 커스텀 설정이 불필요하다. 빈 주입 대신 내부 인스턴스로
    // 두어 JPA 슬라이스 등 ObjectMapper 빈이 없는 테스트 컨텍스트에도 의존을 만들지 않는다.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final NotificationHistoryJpaRepository jpaRepository;

    @Override
    @Transactional
    public void saveAll(List<NotificationHistory> histories) {
        List<NotificationHistoryEntity> entities = histories.stream()
                .map(h -> NotificationHistoryEntity.from(h, writeJson(h.payload())))
                .toList();
        jpaRepository.saveAll(entities);
    }

    @Override
    public List<NotificationHistory> findByUserId(Long userId, Long cursor, int size) {
        PageRequest page = PageRequest.of(0, size);
        List<NotificationHistoryEntity> rows = (cursor == null)
                ? jpaRepository.findByUserIdOrderByIdDesc(userId, page)
                : jpaRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, cursor, page);
        return rows.stream()
                .map(e -> e.toDomain(readJson(e.payloadJson())))
                .toList();
    }

    @Override
    public long countUnread(Long userId) {
        return jpaRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    @Transactional
    public void markAllRead(Long userId) {
        jpaRepository.markAllRead(userId);
    }

    @Override
    @Transactional
    public int deleteOlderThan(LocalDateTime threshold) {
        return jpaRepository.deleteByCreatedAtBefore(threshold);
    }

    private String writeJson(Map<String, String> payload) {
        if (payload == null || payload.isEmpty()) {
            return "{}";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("알림 payload 직렬화 실패 — 빈 payload로 저장", e);
            return "{}";
        }
    }

    private Map<String, String> readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            log.warn("알림 payload 역직렬화 실패 — 빈 payload 반환", e);
            return Map.of();
        }
    }
}
