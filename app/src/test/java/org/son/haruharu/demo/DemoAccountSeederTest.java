package org.son.haruharu.demo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.user.business.UserService;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class DemoAccountSeederTest {

    @Mock UserService userService;

    @Test
    @DisplayName("DEMO_PROVISION_USER_IDS의 각 계정을 프로비저닝한다")
    void givenCsvUserIds_whenRun_thenProvisionsEach() {
        DemoAccountSeeder seeder = new DemoAccountSeeder(userService, "1, 2");

        seeder.run(new DefaultApplicationArguments());

        then(userService).should().provisionDemo(eq(1L), eq("MALE"), any(LocalDate.class), eq(200L));
        then(userService).should().provisionDemo(eq(2L), eq("MALE"), any(LocalDate.class), eq(200L));
    }

    @Test
    @DisplayName("env 미설정이면 아무 계정도 프로비저닝하지 않는다(no-op)")
    void givenEmptyCsv_whenRun_thenNoOp() {
        DemoAccountSeeder seeder = new DemoAccountSeeder(userService, "");

        seeder.run(new DefaultApplicationArguments());

        then(userService).should(never()).provisionDemo(anyLong(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("비숫자 토큰이 섞여도 부팅을 깨지 않고 유효한 id만 프로비저닝한다")
    void givenMalformedToken_whenRun_thenSkipsBadAndProvisionsValid() {
        DemoAccountSeeder seeder = new DemoAccountSeeder(userService, "1,abc,2");

        seeder.run(new DefaultApplicationArguments());

        then(userService).should().provisionDemo(eq(1L), any(), any(), anyLong());
        then(userService).should().provisionDemo(eq(2L), any(), any(), anyLong());
        then(userService).should(never()).provisionDemo(eq(0L), any(), any(), anyLong());
    }

    @Test
    @DisplayName("한 계정 프로비저닝 실패가 다른 계정·부팅을 막지 않는다")
    void givenOneFailure_whenRun_thenContinuesOthers() {
        willThrow(new RuntimeException("not found"))
                .given(userService).provisionDemo(eq(1L), any(), any(), anyLong());
        DemoAccountSeeder seeder = new DemoAccountSeeder(userService, "1,2");

        seeder.run(new DefaultApplicationArguments());

        then(userService).should().provisionDemo(eq(2L), any(), any(), anyLong());
    }
}
