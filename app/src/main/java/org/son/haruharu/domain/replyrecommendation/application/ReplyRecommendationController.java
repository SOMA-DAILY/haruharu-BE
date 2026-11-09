package org.son.haruharu.domain.replyrecommendation.application;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.replyrecommendation.business.ReplyRecommendationEventService;
import org.son.haruharu.domain.replyrecommendation.business.ReplyRecommendationService;
import org.son.haruharu.domain.replyrecommendation.business.command.RecommendationEventCommand;
import org.son.haruharu.domain.replyrecommendation.business.dto.RecommendationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 영상 답장 추천(#558). 시청자 id 는 토큰에서만 읽는다(본문에 viewer_id 없음). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rooms/{roomId}/video-posts/{videoId}")
public class ReplyRecommendationController {

    private final ReplyRecommendationService service;
    private final ReplyRecommendationEventService events;

    /** 유효한 결과를 돌려주거나 작업을 만든다. 준비 중이면 202, 나머지는 200. */
    @PostMapping("/reply-recommendations")
    public ResponseEntity<ApiResponse<RecommendationResponse>> ensure(
            @PathVariable Long roomId, @PathVariable Long videoId, Authentication auth) {
        RecommendationResponse response = service.ensure(roomId, videoId, (Long) auth.getPrincipal());
        return ResponseEntity.status(response.pending() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(ApiResponse.ok(response));
    }

    /** 조회만 한다. 아직 요청이 없으면 UNAVAILABLE(NOT_REQUESTED). */
    @GetMapping("/reply-recommendations")
    public ApiResponse<RecommendationResponse> get(
            @PathVariable Long roomId, @PathVariable Long videoId, Authentication auth) {
        return ApiResponse.ok(service.get(roomId, videoId, (Long) auth.getPrincipal()));
    }

    /** 노출·선택·입력 시작·닫기. 형식 오류는 422, 같은 event_id 다른 내용은 409. */
    @PostMapping("/reply-recommendation-events")
    public ResponseEntity<Void> event(@PathVariable Long roomId, @PathVariable Long videoId,
                                      @RequestBody EventRequest request, Authentication auth) {
        events.record(roomId, videoId, (Long) auth.getPrincipal(), request.toCommand());
        return ResponseEntity.noContent().build();
    }

    /** 형식 검사는 서비스가 한다(422). 초안 글 같은 다른 칸은 받지 않는다. */
    public record EventRequest(String eventId, String sessionId, String requestId, String setId, String cardId,
                               String eventType, String occurredAt) {
        RecommendationEventCommand toCommand() {
            return new RecommendationEventCommand(eventId, sessionId, requestId, setId, cardId, eventType,
                    occurredAt);
        }
    }
}
