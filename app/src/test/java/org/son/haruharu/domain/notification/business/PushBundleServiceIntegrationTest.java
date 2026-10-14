package org.son.haruharu.domain.notification.business;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.notification.business.PushBundleService.ChatDecision;
import org.son.haruharu.domain.notification.business.PushBundleService.UploadDecision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 묶음 상태가 실제 DB에 저장·잠금 조회되어 다음 이벤트의 판단에 쓰이는지 확인한다(#505).
 * 테스트마다 다른 방·유저 id 를 써서 서로의 상태를 공유하지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class PushBundleServiceIntegrationTest {

    @Autowired PushBundleService pushBundleService;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    @DisplayName("같은 방의 이어진 업로드는 처음만 울리고 다음부턴 이름을 모두 담아 조용히 묶는다")
    void givenConsecutiveUploads_whenDecide_thenFirstAlertsThenQuietWithNames() {
        List<UploadDecision> first = pushBundleService.decideUpload(9101L, "노을", List.of(9201L, 9202L));
        List<UploadDecision> second = pushBundleService.decideUpload(9101L, "바다", List.of(9201L));

        assertThat(first).extracting(UploadDecision::alert).containsExactly(true, true);
        assertThat(second).singleElement().satisfies(d -> {
            assertThat(d.alert()).isFalse();
            assertThat(d.names()).containsExactly("노을", "바다");
        });
        // 기존 행을 고쳐도 처음 만든 시각은 그대로다.
        assertThat(jdbc.queryForObject("select count(*) from push_bundles where user_id=9201 and bundle_key='upload:9101'"
                + " and created_at <= updated_at and created_at is not null", Integer.class)).isEqualTo(1);
        // 다른 방은 따로 묶는다.
        assertThat(pushBundleService.decideUpload(9102L, "바다", List.of(9201L)))
                .singleElement().satisfies(d -> assertThat(d.alert()).isTrue());
    }

    @Test
    @DisplayName("단체 채팅은 같은 방에서 처음만 울리고 읽지 않은 동안 이어진 메시지는 조용히 묶는다")
    void givenConsecutiveGroupMessages_whenDecide_thenFirstAlertsThenQuiet() {
        List<ChatDecision> first = pushBundleService.decideGroupChat(9301L, 1L, List.of(9401L));
        List<ChatDecision> second = pushBundleService.decideGroupChat(9301L, 2L, List.of(9401L, 9402L));

        assertThat(first).singleElement().satisfies(d -> assertThat(d.alert()).isTrue());
        assertThat(second).extracting(ChatDecision::userId, ChatDecision::alert)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(9401L, false),
                        org.assertj.core.groups.Tuple.tuple(9402L, true));
    }

    @Test
    @DisplayName("같은 방 첫 업로드가 동시에 들어와 첫 행 삽입이 겹쳐도, 실패한 쪽을 다시 판단하면 두 이름이 한 칸에 묶인다")
    void givenConcurrentFirstUploads_whenRetryFailed_thenBothNamesBundled() throws Exception {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);
        try {
            var first = pool.submit(() -> { gate.await(); return decideOrNull(9501L, "노을"); });
            var second = pool.submit(() -> { gate.await(); return decideOrNull(9501L, "바다"); });
            gate.countDown();
            List<UploadDecision> a = first.get(30, java.util.concurrent.TimeUnit.SECONDS);
            List<UploadDecision> b = second.get(30, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(a != null || b != null).isTrue();
            // 실패한 쪽은 리스너처럼 한 번 다시 판단한다.
            if (a == null) a = pushBundleService.decideUpload(9501L, "노을", List.of(9601L, 9602L));
            if (b == null) b = pushBundleService.decideUpload(9501L, "바다", List.of(9601L, 9602L));
            long alerts = java.util.stream.Stream.of(a, b).flatMap(List::stream).filter(UploadDecision::alert).count();
            assertThat(alerts).isEqualTo(2); // 수신자마다 한 번만 울린다
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForList("select names from push_bundles where bundle_key='upload:9501' order by user_id", String.class))
                .hasSize(2).allSatisfy(names -> assertThat(names.split("\n")).containsExactlyInAnyOrder("노을", "바다"));
    }

    private List<UploadDecision> decideOrNull(Long roomId, String name) {
        try {
            return pushBundleService.decideUpload(roomId, name, List.of(9601L, 9602L));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
