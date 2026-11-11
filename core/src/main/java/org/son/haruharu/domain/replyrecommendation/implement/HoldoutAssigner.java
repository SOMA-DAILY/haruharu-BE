package org.son.haruharu.domain.replyrecommendation.implement;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.springframework.stereotype.Component;

/**
 * 추천 효과 무작위 비교 그룹(이슈 #560). 단위는 시청자이고, 한 사람은 그 기수 동안 같은 그룹이다.
 * <p>
 * 방의 첫 요청 때 그 방 멤버 전원의 해시 {@code sha256(salt:groupKey:userId)} 를 [0, 1) 값으로 바꿔 오름차순으로 줄 세운다.
 * 앞 {@code floor(n × (1 − ratio))} 명은 TREATMENT, 뒤 {@code floor(n × ratio)} 명은 HOLDOUT, 남는 1명은 해시가
 * {@code 1 − ratio} 이상이면 HOLDOUT 이다. 그 뒤에 들어온 멤버는 해시가 {@code 1 − ratio} 이상이면 HOLDOUT 이다.
 * 배정은 표에 한 번 넣고 바꾸지 않는다. 비율이 0 이거나 561 SQL 이 없으면 NONE 이고 아무것도 쓰지 않는다.
 */
@Component
@RequiredArgsConstructor
public class HoldoutAssigner {

    private static final Logger log = LoggerFactory.getLogger(HoldoutAssigner.class);
    private static final double TWO_TO_64 = Math.pow(2, 64);

    private final ReplyHoldoutProperties properties;
    private final HoldoutAssignmentRepository repository;
    private final RoomReader rooms;
    private final RoomMemberReader members;

    private volatile boolean schemaWarned;

    /** 561 표·칸이 있어 그룹을 기록할 수 있는지. */
    public boolean recordable() {
        return repository.schemaReady();
    }

    /** 이 방에서 이 시청자의 그룹. 실험이 꺼져 있거나 표가 없으면 NONE(배정하지 않는다). */
    public ExperimentArm armOf(Long roomId, Long userId, Instant now) {
        if (!properties.enabled() || roomId == null || userId == null) {
            return ExperimentArm.NONE;
        }
        if (!repository.schemaReady()) {
            if (!schemaWarned) {
                schemaWarned = true;
                log.atWarn().addKeyValue("event", "REPLY_HOLDOUT_SCHEMA_MISSING")
                        .log("비교 그룹 비율이 켜져 있지만 561 SQL 이 없어 배정하지 않음");
            }
            return ExperimentArm.NONE;
        }
        Optional<ExperimentArm> existing = repository.findArm(roomId, userId);
        if (existing.isPresent()) {
            return existing.get();
        }
        // 같은 방의 첫 요청 둘이 동시에 오면 멤버 구성이 다른 두 분할이 섞일 수 있다. 잠근 뒤 다시 확인한다.
        repository.lockRoom(roomId);
        Optional<ExperimentArm> raced = repository.findArm(roomId, userId);
        if (raced.isPresent()) {
            return raced.get();
        }
        String groupKey = groupKey(rooms.findById(roomId).orElse(null), roomId);
        List<HoldoutAssignment> assignments = repository.roomAssigned(roomId)
                ? List.of(lateJoiner(groupKey, roomId, userId))
                : firstSplit(groupKey, roomId, userId);
        repository.insertAll(assignments, now);
        return repository.findArm(roomId, userId).orElse(ExperimentArm.NONE);
    }

    private List<HoldoutAssignment> firstSplit(String groupKey, Long roomId, Long userId) {
        List<Long> userIds = new ArrayList<>(members.getByRoomId(roomId).stream().map(RoomMember::userId).distinct().toList());
        if (!userIds.contains(userId)) {
            userIds.add(userId);
        }
        Map<Long, Double> hashes = new LinkedHashMap<>();
        for (Long id : userIds) {
            hashes.put(id, hash(properties.salt(), groupKey, id));
        }
        Map<Long, ExperimentArm> arms = split(hashes, properties.ratio());
        return arms.entrySet().stream()
                .map(e -> new HoldoutAssignment(groupKey, roomId, e.getKey(), e.getValue(), hashes.get(e.getKey()),
                        properties.ratio()))
                .toList();
    }

    private HoldoutAssignment lateJoiner(String groupKey, Long roomId, Long userId) {
        double h = hash(properties.salt(), groupKey, userId);
        ExperimentArm arm = h >= 1 - properties.ratio() ? ExperimentArm.HOLDOUT : ExperimentArm.TREATMENT;
        return new HoldoutAssignment(groupKey, roomId, userId, arm, h, properties.ratio());
    }

    /** 기수가 있으면 기수 단위, 없으면 방 단위. 재참가하면 기수가 달라 다시 계산된다. */
    static String groupKey(Room room, Long roomId) {
        Long cohortId = room == null ? null : room.composition().cohortId();
        return cohortId != null ? "cohort:" + cohortId : "room:" + roomId;
    }

    /** 해시 오름차순 앞쪽 TREATMENT, 뒤쪽 HOLDOUT. 남는 1명은 해시가 1 − ratio 이상이면 HOLDOUT. */
    static Map<Long, ExperimentArm> split(Map<Long, Double> hashes, double ratio) {
        List<Map.Entry<Long, Double>> ordered = hashes.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .toList();
        int n = ordered.size();
        int treatment = (int) Math.floor(n * (1 - ratio) + 1e-9);
        int holdout = (int) Math.floor(n * ratio + 1e-9);
        Map<Long, ExperimentArm> arms = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            Map.Entry<Long, Double> e = ordered.get(i);
            ExperimentArm arm;
            if (i < treatment) {
                arm = ExperimentArm.TREATMENT;
            } else if (i >= n - holdout) {
                arm = ExperimentArm.HOLDOUT;
            } else {
                arm = e.getValue() >= 1 - ratio ? ExperimentArm.HOLDOUT : ExperimentArm.TREATMENT;
            }
            arms.put(e.getKey(), arm);
        }
        return arms;
    }

    static double hash(String salt, String groupKey, Long userId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((salt + ":" + groupKey + ":" + userId).getBytes(StandardCharsets.UTF_8));
            long first = ByteBuffer.wrap(digest, 0, Long.BYTES).getLong();
            double unsigned = first >= 0 ? first : first + TWO_TO_64;
            return Math.min(unsigned / TWO_TO_64, Math.nextDown(1.0));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 없음", e);
        }
    }
}
