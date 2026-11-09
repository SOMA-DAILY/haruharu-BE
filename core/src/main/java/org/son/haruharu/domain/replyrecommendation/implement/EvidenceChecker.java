package org.son.haruharu.domain.replyrecommendation.implement;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMessageReader;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.springframework.stereotype.Component;

/** 분석·개인화 입력에 들어간 원문이 지금도 남아 있는지(채팅: 운영자 삭제·퇴장 멤버, 이전 영상: 삭제·숨김). */
@Component
@RequiredArgsConstructor
public class EvidenceChecker {

    private final AnalysisPipelineRepository pipeline;
    private final ChatMessageReader chatMessages;
    private final VideoPostReader videoPosts;

    public boolean anyDeleted(EvidenceOwner owner, Long ownerId) {
        if (ownerId == null) {
            return false;
        }
        List<EvidenceRef> refs = pipeline.findEvidence(owner, ownerId);
        Set<Long> chatIds = refs.stream().filter(EvidenceRef::chat).map(EvidenceRef::sourceRefId)
                .collect(Collectors.toSet());
        if (!chatIds.isEmpty()) {
            Set<Long> visible = chatMessages.findAllByIds(chatIds).stream()
                    .filter(message -> !message.isAdminDeleted())
                    .map(ChatMessage::id)
                    .collect(Collectors.toSet());
            if (!visible.containsAll(chatIds)) {
                return true;
            }
        }
        Set<Long> videoIds = refs.stream().filter(EvidenceRef::video).map(EvidenceRef::sourceRefId)
                .collect(Collectors.toSet());
        return !videoIds.isEmpty() && !videoPosts.findUnavailableIds(videoIds).isEmpty();
    }
}
