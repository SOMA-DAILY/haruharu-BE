package org.son.haruharu.domain.mission.business;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.MissionReader;
import org.son.haruharu.domain.mission.implement.MissionWriter;
import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.mission.implement.UploadSlotReader;
import org.son.haruharu.domain.mission.implement.UploadSlotWriter;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 참조 데이터(업로드 슬롯 5/5/3, 미션 풀) 시드. 비어 있을 때만 1회 삽입(멱등).
 * 슬롯 시간창·미션 내용/포인트는 운영 확정 전 잠정값 — 추후 어드민 큐레이션으로 대체.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissionCatalogSeeder implements ApplicationRunner {

    private final UploadSlotReader uploadSlotReader;
    private final UploadSlotWriter uploadSlotWriter;
    private final MissionReader missionReader;
    private final MissionWriter missionWriter;

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        seedSlots();
        seedMissions();
    }

    private void seedSlots() {
        if (!uploadSlotReader.getAll().isEmpty()) {
            return;
        }
        List<UploadSlot> slots = List.of(
                // Day 1 (5)
                UploadSlot.of(1, 1, 8, 11),
                UploadSlot.of(1, 2, 11, 14),
                UploadSlot.of(1, 3, 14, 17),
                UploadSlot.of(1, 4, 17, 20),
                UploadSlot.of(1, 5, 20, 24),
                // Day 2 (5)
                UploadSlot.of(2, 1, 8, 11),
                UploadSlot.of(2, 2, 11, 14),
                UploadSlot.of(2, 3, 14, 17),
                UploadSlot.of(2, 4, 17, 20),
                UploadSlot.of(2, 5, 20, 24),
                // Day 3 (3)
                UploadSlot.of(3, 1, 8, 13),
                UploadSlot.of(3, 2, 13, 19),
                UploadSlot.of(3, 3, 19, 24)
        );
        uploadSlotWriter.saveAll(slots);
        log.info("업로드 슬롯 시드 완료: {}개", slots.size());
    }

    private void seedMissions() {
        if (!missionReader.getActiveCandidates().isEmpty()) {
            return;
        }
        List<Mission> missions = List.of(
                Mission.of("오늘 먹은 가장 맛있는 음식을 보여주세요", 5),
                Mission.of("지금 보이는 풍경을 찍어 공유해요", 5),
                Mission.of("요즘 빠져 있는 취미를 소개해주세요", 5),
                Mission.of("오늘 기분을 한 단어로 표현하면?", 5),
                Mission.of("가장 좋아하는 노래를 들려주세요", 5),
                Mission.of("최근에 산 마음에 드는 물건 자랑하기", 5),
                Mission.of("나만의 스트레스 해소법 공유하기", 5),
                Mission.of("오늘 하루 중 가장 좋았던 순간은?", 5)
        );
        missionWriter.saveAll(missions);
        log.info("미션 풀 시드 완료: {}개", missions.size());
    }
}
