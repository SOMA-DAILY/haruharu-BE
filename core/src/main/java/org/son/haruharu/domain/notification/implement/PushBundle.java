package org.son.haruharu.domain.notification.implement;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 연달아 오는 푸시를 한 알림으로 묶는 수신자별 상태(#505). bundleKey 하나가 기기 알림 한 칸(iOS collapse-id, Android tag)이다.
 * - 단체 채팅: 마지막 울림 후 30분이 지났거나, 울린 메시지까지 읽었고 10분이 지났으면 다시 울린다. 그 밖에는 조용히 교체한다.
 *   lastAlertedRef = 마지막으로 울린 메시지 id.
 * - 영상 업로드: 첫 업로드에 울리고 60분 동안 이어진 업로드는 조용히 교체하며 올린 사람 이름을 모두 모은다.
 * 기준 시간은 운영 데이터 시뮬레이션으로 정했다(1인 하루 단체 채팅 183→약 12, 업로드 48→약 11).
 */
public record PushBundle(
        Long id,
        Long userId,
        String bundleKey,
        LocalDateTime lastAlertedAt,
        Long lastAlertedRef,
        List<String> names
) {
    static final Duration CHAT_MAX_SILENCE = Duration.ofMinutes(30);
    static final Duration CHAT_MIN_GAP_AFTER_READ = Duration.ofMinutes(10);
    static final Duration UPLOAD_WINDOW = Duration.ofMinutes(60);

    public PushBundle {
        names = names == null ? List.of() : List.copyOf(names);
    }

    public static String chatKey(Long chatRoomId) {
        return "chat:" + chatRoomId;
    }

    public static String uploadKey(Long roomId) {
        return "upload:" + roomId;
    }

    /** lastReadMessageId = 수신자의 현재 읽음 커서(없으면 null). */
    public static boolean shouldAlertChat(PushBundle bundle, LocalDateTime now, Long lastReadMessageId) {
        if (bundle == null) {
            return true;
        }
        Duration elapsed = Duration.between(bundle.lastAlertedAt, now);
        if (elapsed.compareTo(CHAT_MAX_SILENCE) >= 0) {
            return true;
        }
        boolean readSinceAlert = lastReadMessageId != null && bundle.lastAlertedRef != null
                && lastReadMessageId >= bundle.lastAlertedRef;
        return readSinceAlert && elapsed.compareTo(CHAT_MIN_GAP_AFTER_READ) >= 0;
    }

    public static PushBundle alertedChat(Long userId, String bundleKey, LocalDateTime now, Long messageId) {
        return new PushBundle(null, userId, bundleKey, now, messageId, List.of());
    }

    public PushBundle alertedChat(LocalDateTime now, Long messageId) {
        return new PushBundle(id, userId, bundleKey, now, messageId, List.of());
    }

    public static UploadStep addUploader(PushBundle bundle, Long userId, String bundleKey, String name, LocalDateTime now) {
        if (bundle == null || Duration.between(bundle.lastAlertedAt, now).compareTo(UPLOAD_WINDOW) >= 0) {
            Long id = bundle == null ? null : bundle.id;
            return new UploadStep(new PushBundle(id, userId, bundleKey, now, null, List.of(name)), true);
        }
        List<String> names = new ArrayList<>(bundle.names);
        if (!names.contains(name)) {
            names.add(name);
        }
        return new UploadStep(new PushBundle(bundle.id, userId, bundleKey, bundle.lastAlertedAt, null, names), false);
    }

    /** "OO 외 N명"으로 줄이지 않는다 — 누가 올렸는지 모두 보여야 한다. */
    public static String uploadTitle(List<String> names) {
        return "🎬 " + names.stream().map(name -> name + "님").collect(Collectors.joining(", ")) + "이 하루를 남겼어요";
    }

    public static String chatTitle(long unreadCount) {
        return unreadCount > 1 ? "💬 새 메시지 " + unreadCount + "개" : "💬 새 메시지";
    }

    public record UploadStep(PushBundle bundle, boolean alert) {
    }
}
