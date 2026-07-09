package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.coin.business.command.CoinCreditCommand;
import org.son.haruharu.domain.coin.implement.CoinBucket;
import org.son.haruharu.domain.coin.implement.CoinLedgerReason;
import org.son.haruharu.domain.room.implement.DailyUploadRewardReader;
import org.son.haruharu.domain.room.implement.DailyUploadRewardWriter;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.video.implement.VideoPostReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UploadRewardServiceTest {

    @Mock VideoPostReader videoPostReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock DailyUploadRewardReader dailyUploadRewardReader;
    @Mock DailyUploadRewardWriter dailyUploadRewardWriter;
    @Mock CoinService coinService;

    private final UploadRewardProperties props = new UploadRewardProperties(Map.of(1, 2L, 2, 2L, 3, 1L));
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-26T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    private UploadRewardService service() {
        return new UploadRewardService(videoPostReader, roomMemberReader,
                dailyUploadRewardReader, dailyUploadRewardWriter, coinService, props, clock);
    }

    private RoomMember member(Long memberId, Long userId) {
        return new RoomMember(memberId, 1L, userId, "MALE", null, LocalDateTime.now());
    }

    @Test
    @DisplayName("Day1 슬롯 5개를 모두 채우고 미지급이면 구름 2를 REWARD로 적립하고 지급 기록을 남긴다")
    void givenDay1Completed_whenGrant_thenCreditsAndRecords() {
        Long memberId = 10L;
        Long userId = 99L;
        given(videoPostReader.countUploaded(memberId, 1)).willReturn(5L);
        given(dailyUploadRewardReader.isRewarded(memberId, 1)).willReturn(false);
        given(roomMemberReader.getById(memberId)).willReturn(member(memberId, userId));

        service().grantIfDayCompleted(memberId, 1);

        then(dailyUploadRewardWriter).should().save(any());
        ArgumentCaptor<CoinCreditCommand> captor = ArgumentCaptor.forClass(CoinCreditCommand.class);
        then(coinService).should().credit(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(userId);
        assertThat(captor.getValue().amount()).isEqualTo(2);
        assertThat(captor.getValue().bucket()).isEqualTo(CoinBucket.REWARD);
        assertThat(captor.getValue().reason()).isEqualTo(CoinLedgerReason.UPLOAD_COMPLETION_REWARD);
    }

    @Test
    @DisplayName("Day1 슬롯이 아직 안 찼으면(4/5) 지급하지 않는다")
    void givenDay1NotCompleted_whenGrant_thenNoGrant() {
        Long memberId = 10L;
        given(videoPostReader.countUploaded(memberId, 1)).willReturn(4L);

        service().grantIfDayCompleted(memberId, 1);

        then(dailyUploadRewardWriter).should(never()).save(any());
        then(coinService).should(never()).credit(any());
    }

    @Test
    @DisplayName("이미 지급된 Day면 완주해도 재지급하지 않는다(멱등)")
    void givenAlreadyRewarded_whenGrant_thenNoGrant() {
        Long memberId = 10L;
        given(videoPostReader.countUploaded(memberId, 1)).willReturn(5L);
        given(dailyUploadRewardReader.isRewarded(memberId, 1)).willReturn(true);

        service().grantIfDayCompleted(memberId, 1);

        then(dailyUploadRewardWriter).should(never()).save(any());
        then(coinService).should(never()).credit(any());
    }

    @Test
    @DisplayName("Day3은 슬롯 3개를 채우면 구름 1을 지급한다(슬롯 수는 enum 단일 출처)")
    void givenDay3Completed_whenGrant_thenCreditsOne() {
        Long memberId = 10L;
        Long userId = 99L;
        given(videoPostReader.countUploaded(memberId, 3)).willReturn(3L);
        given(dailyUploadRewardReader.isRewarded(memberId, 3)).willReturn(false);
        given(roomMemberReader.getById(memberId)).willReturn(member(memberId, userId));

        service().grantIfDayCompleted(memberId, 3);

        ArgumentCaptor<CoinCreditCommand> captor = ArgumentCaptor.forClass(CoinCreditCommand.class);
        then(coinService).should().credit(captor.capture());
        assertThat(captor.getValue().amount()).isEqualTo(1);
    }

    @Test
    @DisplayName("슬롯이 없는 Day(미시작 0)면 아무것도 하지 않는다")
    void givenDayZero_whenGrant_thenNoGrant() {
        service().grantIfDayCompleted(10L, 0);

        then(coinService).should(never()).credit(any());
        then(dailyUploadRewardWriter).should(never()).save(any());
    }
}
