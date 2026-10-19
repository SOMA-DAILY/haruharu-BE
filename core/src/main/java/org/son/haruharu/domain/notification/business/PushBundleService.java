package org.son.haruharu.domain.notification.business;

import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.chat.implement.ChatMessageReader;
import org.son.haruharu.domain.chat.implement.ChatRoomMember;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.domain.notification.implement.PushBundle;
import org.son.haruharu.domain.notification.implement.PushBundleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 수신자별로 이번 푸시를 울릴지, 앞선 알림을 조용히 교체할지 정한다(#505). 규칙은 {@link PushBundle}.
 * 상태 행을 잠가 갱신하므로 같은 묶음에 동시에 들어온 이벤트가 둘 다 울리지 않는다.
 * 판단이 실패하면 호출자는 보통 알림으로 대신 보낸다.
 */
@Service
public class PushBundleService {

    private final PushBundleRepository pushBundleRepository;
    private final ChatRoomMemberReader chatRoomMemberReader;
    private final ChatMessageReader chatMessageReader;
    private final BlockReader blockReader;
    private final Clock clock;

    public PushBundleService(PushBundleRepository pushBundleRepository, ChatRoomMemberReader chatRoomMemberReader,
                             ChatMessageReader chatMessageReader, BlockReader blockReader, Clock clock) {
        this.pushBundleRepository = pushBundleRepository;
        this.chatRoomMemberReader = chatRoomMemberReader;
        this.chatMessageReader = chatMessageReader;
        this.blockReader = blockReader;
        this.clock = clock;
    }

    /**
     * unreadCount 는 앱 채팅 목록과 같은 기준(내 메시지·내가 차단한 상대 메시지 제외)이다.
     * 채팅 전달 트랜잭션(ChatDeliveryService.deliverIfActive)에 합류한다 — 방 잠금이 같은 방 메시지를 직렬화하므로
     * 첫 행 삽입이 겹치지 않고, 커넥션을 하나 더 잡지 않는다.
     */
    @Transactional
    public List<ChatDecision> decideGroupChat(Long chatRoomId, Long messageId, List<Long> recipients) {
        LocalDateTime now = LocalDateTime.now(clock);
        String key = PushBundle.chatKey(chatRoomId);
        Map<Long, PushBundle> bundles = byUser(pushBundleRepository.findAllForUpdate(recipients, key));
        Map<Long, Long> readCursors = new HashMap<>();
        for (ChatRoomMember member : chatRoomMemberReader.findByChatRoomId(chatRoomId)) {
            readCursors.put(member.userId(), member.lastReadMessageId());
        }
        List<ChatDecision> decisions = new ArrayList<>();
        List<PushBundle> changed = new ArrayList<>();
        for (Long userId : recipients) {
            PushBundle bundle = bundles.get(userId);
            Long lastRead = readCursors.get(userId);
            boolean alert = PushBundle.shouldAlertChat(bundle, now, lastRead);
            if (alert) {
                changed.add(bundle == null
                        ? PushBundle.alertedChat(userId, key, now, messageId)
                        : bundle.alertedChat(now, messageId));
            }
            Set<Long> excluded = new HashSet<>(blockReader.getBlockedUserIds(userId));
            excluded.add(userId);
            decisions.add(new ChatDecision(userId, alert, chatMessageReader.countUnread(chatRoomId, lastRead, excluded)));
        }
        pushBundleRepository.saveAll(changed);
        return decisions;
    }

    /**
     * 업로드는 잠글 방 행이 없어 동시에 들어온 첫 업로드끼리 첫 행 삽입이 겹칠 수 있다(유니크 충돌·교착).
     * 이때 예외를 던지고 롤백하므로 호출자가 한 번 다시 부른다. 잠금·삽입 순서를 유저 id 순으로 맞춰 교착을 줄인다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<UploadDecision> decideUpload(Long roomId, String uploaderName, List<Long> recipients) {
        LocalDateTime now = LocalDateTime.now(clock);
        String key = PushBundle.uploadKey(roomId);
        Map<Long, PushBundle> bundles = byUser(pushBundleRepository.findAllForUpdate(recipients, key));
        List<UploadDecision> decisions = new ArrayList<>();
        List<PushBundle> changed = new ArrayList<>();
        for (Long userId : recipients.stream().sorted().toList()) {
            PushBundle.UploadStep step = PushBundle.addUploader(bundles.get(userId), userId, key, uploaderName, now);
            changed.add(step.bundle());
            decisions.add(new UploadDecision(userId, step.alert(), step.bundle().names()));
        }
        pushBundleRepository.saveAll(changed);
        return decisions;
    }

    private static Map<Long, PushBundle> byUser(List<PushBundle> bundles) {
        return bundles.stream().collect(Collectors.toMap(PushBundle::userId, Function.identity()));
    }

    public record ChatDecision(Long userId, boolean alert, long unreadCount) {
    }

    public record UploadDecision(Long userId, boolean alert, List<String> names) {
    }
}
