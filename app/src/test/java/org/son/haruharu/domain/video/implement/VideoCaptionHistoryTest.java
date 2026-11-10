package org.son.haruharu.domain.video.implement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VideoCaptionHistoryTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 8, 14, 0);

    @Mock VideoCaptionRevisionRepository repository;

    @Test
    @DisplayName("기준 시각 뒤에 바뀐 캡션은 가장 이른 수정의 이전 값으로 복원한다(TIME02)")
    void givenChangesAfterCutoff_whenCaptionsAt_thenEarliestPreviousValue() {
        given(repository.findChangedAfter(any(), any())).willReturn(List.of(
                new VideoCaptionRevision(1L, 70L, "원래", "첫 수정", T.plusHours(1)),
                new VideoCaptionRevision(2L, 70L, "첫 수정", "두 번째", T.plusHours(2))));

        var captions = new VideoCaptionHistory(repository).captionsAt(List.of(70L, 71L), T);

        assertThat(captions).containsEntry(70L, "원래").doesNotContainKey(71L);
    }

    @Test
    @DisplayName("같은 문구로 저장하면 이력을 남기지 않는다")
    void givenSameCaption_whenRecord_thenSkips() {
        new VideoCaptionHistory(repository).record(70L, "같음", "같음", T);

        then(repository).shouldHaveNoInteractions();
    }
}
