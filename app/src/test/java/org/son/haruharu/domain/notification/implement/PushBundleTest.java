package org.son.haruharu.domain.notification.implement;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PushBundleTest {

    static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 14, 20, 0);
    static final String CHAT_KEY = PushBundle.chatKey(9L);
    static final String UPLOAD_KEY = PushBundle.uploadKey(3L);

    @Test
    @DisplayName("단체 채팅: 처음 받는 메시지는 울린다")
    void givenNoBundle_whenChat_thenAlerts() {
        assertThat(PushBundle.shouldAlertChat(null, T0, null)).isTrue();
    }

    @Test
    @DisplayName("단체 채팅: 울린 뒤 읽지 않았으면 30분 전까지는 조용히 합친다")
    void givenUnreadSinceAlert_whenWithin30Minutes_thenQuiet() {
        PushBundle bundle = PushBundle.alertedChat(1L, CHAT_KEY, T0, 100L);

        assertThat(PushBundle.shouldAlertChat(bundle, T0.plusMinutes(29), 99L)).isFalse();
        assertThat(PushBundle.shouldAlertChat(bundle, T0.plusMinutes(29), null)).isFalse();
    }

    @Test
    @DisplayName("단체 채팅: 읽지 않았어도 마지막 울림 후 30분이 지나면 다시 울린다")
    void givenUnread_when30MinutesPassed_thenAlerts() {
        PushBundle bundle = PushBundle.alertedChat(1L, CHAT_KEY, T0, 100L);

        assertThat(PushBundle.shouldAlertChat(bundle, T0.plusMinutes(30), 99L)).isTrue();
    }

    @Test
    @DisplayName("단체 채팅: 울린 메시지까지 읽었으면 10분이 지난 뒤 다시 울린다")
    void givenReadSinceAlert_whenAfter10Minutes_thenAlerts() {
        PushBundle bundle = PushBundle.alertedChat(1L, CHAT_KEY, T0, 100L);

        assertThat(PushBundle.shouldAlertChat(bundle, T0.plusMinutes(9), 100L)).isFalse();
        assertThat(PushBundle.shouldAlertChat(bundle, T0.plusMinutes(10), 100L)).isTrue();
        assertThat(PushBundle.shouldAlertChat(bundle, T0.plusMinutes(10), 150L)).isTrue();
    }

    @Test
    @DisplayName("영상 업로드: 첫 업로드는 울리고 이름 하나로 시작한다")
    void givenNoBundle_whenUpload_thenAlertsWithOneName() {
        PushBundle.UploadStep step = PushBundle.addUploader(null, 1L, UPLOAD_KEY, "민지", T0);

        assertThat(step.alert()).isTrue();
        assertThat(step.bundle().names()).containsExactly("민지");
        assertThat(step.bundle().lastAlertedAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("영상 업로드: 60분 안의 업로드는 조용히 이름을 모두 이어 붙인다(같은 사람은 한 번만)")
    void givenOpenWindow_whenUpload_thenQuietWithAllNames() {
        PushBundle first = PushBundle.addUploader(null, 1L, UPLOAD_KEY, "민지", T0).bundle();
        PushBundle second = PushBundle.addUploader(first, 1L, UPLOAD_KEY, "서연", T0.plusMinutes(20)).bundle();
        PushBundle.UploadStep third = PushBundle.addUploader(second, 1L, UPLOAD_KEY, "민지", T0.plusMinutes(59));

        assertThat(third.alert()).isFalse();
        assertThat(third.bundle().names()).containsExactly("민지", "서연");
        assertThat(third.bundle().lastAlertedAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("영상 업로드: 첫 울림 후 60분이 지나면 새로 울리고 이름을 새로 모은다")
    void givenExpiredWindow_whenUpload_thenAlertsAndRestarts() {
        PushBundle first = PushBundle.addUploader(null, 1L, UPLOAD_KEY, "민지", T0).bundle();
        PushBundle.UploadStep step = PushBundle.addUploader(first, 1L, UPLOAD_KEY, "서연", T0.plusMinutes(60));

        assertThat(step.alert()).isTrue();
        assertThat(step.bundle().names()).containsExactly("서연");
        assertThat(step.bundle().lastAlertedAt()).isEqualTo(T0.plusMinutes(60));
    }

    @Test
    @DisplayName("업로드 제목은 이름을 줄이지 않고 모두 나열한다")
    void givenNames_whenUploadTitle_thenListsEveryName() {
        assertThat(PushBundle.uploadTitle(List.of("민지"))).isEqualTo("🎬 민지님이 하루를 남겼어요");
        assertThat(PushBundle.uploadTitle(List.of("민지", "서연", "하늘")))
                .isEqualTo("🎬 민지님, 서연님, 하늘님이 하루를 남겼어요");
    }

    @Test
    @DisplayName("단체 채팅 제목은 안 읽은 메시지가 여러 개면 개수를 붙인다")
    void givenUnreadCount_whenChatTitle_thenShowsCount() {
        assertThat(PushBundle.chatTitle(1)).isEqualTo("💬 새 메시지");
        assertThat(PushBundle.chatTitle(0)).isEqualTo("💬 새 메시지");
        assertThat(PushBundle.chatTitle(12)).isEqualTo("💬 새 메시지 12개");
    }
}
