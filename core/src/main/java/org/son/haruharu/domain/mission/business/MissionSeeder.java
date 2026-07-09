package org.son.haruharu.domain.mission.business;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.MissionReader;
import org.son.haruharu.domain.mission.implement.MissionWriter;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 미션 풀 시드. 비어 있을 때만 1회 삽입(멱등). 미션 내용/포인트는 운영 확정 전 잠정값 — 어드민 CRUD는 후속.
 * MVP 동안 미션 기능 비활성화로 @Component를 제거해 시드를 실행하지 않는다.
 * 미션 재개 시 @Component를 복원하면 된다(클래스·시드 데이터는 유지).
 */
@Slf4j
@RequiredArgsConstructor
public class MissionSeeder implements ApplicationRunner {

    private final MissionReader missionReader;
    private final MissionWriter missionWriter;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
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
