package org.son.haruharu.demo;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.room.business.DemoRoomService;
import org.son.haruharu.domain.user.business.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 데모/심사 계정 부팅 시더(#264). env DEMO_PROVISION_USER_IDS(쉼표구분 userId)가 있을 때만
 * 부팅 시 각 계정을 데모 계정으로 프로비저닝한다. 미설정이면 no-op(일반 부팅 무영향).
 * <p>
 * 공개 엔드포인트 없이 env로만 동작해 공격 표면이 없다. CSV 파싱 오류·한 계정 실패가 모두
 * 서버 부팅을 막지 않도록 개별 격리한다(잘못된 값은 로그 후 건너뜀). 리뷰어용 계정은 env 넣고
 * 1회 배포 → 확인 후 env 제거·재배포하는 드문 운영 작업이다.
 */
@Slf4j
@Component
public class DemoAccountSeeder implements ApplicationRunner {

    // 데모 계정 기본값. 리뷰어가 반대 성별 후보를 보도록 남성 기준(반대 성별 계정은 후속 확장).
    private static final String DEMO_GENDER = "MALE";
    private static final LocalDate DEMO_BIRTHDAY = LocalDate.of(1995, 6, 15);
    // 리셋(#267)과 지급 금액이 어긋나지 않도록 단일 상수 공유.
    private static final long DEMO_COINS = DemoRoomService.DEMO_COINS;

    private final UserService userService;
    private final String userIdsCsv;

    public DemoAccountSeeder(UserService userService,
                             @Value("${demo.provision.user-ids:}") String userIdsCsv) {
        this.userService = userService;
        this.userIdsCsv = userIdsCsv;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 파싱을 run() 안에서 개별 격리 — 생성자에서 파싱하면 잘못된 값이 컨텍스트 초기화(=서버 부팅)를 깬다.
        List<Long> userIds = parseUserIds(userIdsCsv);
        if (userIds.isEmpty()) {
            return;
        }
        log.info("데모 계정 프로비저닝 시작: {}건", userIds.size());
        for (Long userId : userIds) {
            try {
                userService.provisionDemo(userId, DEMO_GENDER, DEMO_BIRTHDAY, DEMO_COINS);
                log.info("데모 계정 프로비저닝 완료: userId={}", userId);
            } catch (Exception e) {
                // 한 계정 실패(존재하지 않는 id 등)가 서버 부팅을 막지 않도록 격리한다. 스택까지 남겨 진단성 확보.
                log.warn("데모 계정 프로비저닝 실패: userId={}", userId, e);
            }
        }
    }

    /** 쉼표구분 CSV를 Long 목록으로. 비숫자·공백 등 잘못된 토큰은 부팅을 깨지 않도록 건너뛰고 경고만 남긴다. */
    private List<Long> parseUserIds(String csv) {
        List<Long> ids = new ArrayList<>();
        for (String token : csv.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(trimmed));
            } catch (NumberFormatException e) {
                log.warn("데모 프로비저닝 대상 파싱 실패(건너뜀): token='{}'", trimmed);
            }
        }
        return ids;
    }
}
