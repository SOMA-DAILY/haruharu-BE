package org.son.haruharu.domain.room;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.coin.implement.CoinWalletReader;
import org.son.haruharu.domain.room.business.UploadRewardService;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.storage.DailyUploadRewardJpaRepository;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.son.haruharu.domain.room.storage.RoomMemberJpaRepository;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.storage.VideoPostEntity;
import org.son.haruharu.domain.video.storage.VideoPostJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UploadRewardIntegrationTest {

    @Autowired UploadRewardService uploadRewardService;
    @Autowired RoomMemberJpaRepository roomMemberJpaRepository;
    @Autowired VideoPostJpaRepository videoPostJpaRepository;
    @Autowired DailyUploadRewardJpaRepository dailyUploadRewardJpaRepository;
    @Autowired CoinWalletReader coinWalletReader;
    @Autowired org.son.haruharu.domain.coin.business.CoinService coinService;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM daily_upload_reward");
        jdbcTemplate.update("DELETE FROM coin_ledger");
        jdbcTemplate.update("DELETE FROM coin_wallet");
        jdbcTemplate.update("DELETE FROM video_posts");
        jdbcTemplate.update("DELETE FROM room_members");
    }

    private Long seedMember(Long userId) {
        return roomMemberJpaRepository.save(RoomMemberEntity.from(
                RoomMember.of(1L, userId, "MALE", null, LocalDateTime.now()))).toDomain().id();
    }

    private void seedUpload(Long memberId, int day, int slot) {
        videoPostJpaRepository.save(VideoPostEntity.from(VideoPost.create(
                memberId, day, slot, null, null, "https://cdn/v" + slot + ".mp4", null, LocalDateTime.now())));
    }

    @Test
    @DisplayName("Day1 5슬롯을 모두 채운 멤버에게 완주 보상(구름 5, #143)이 지급되고, 재호출해도 중복 지급되지 않는다")
    void givenDay1AllSlots_whenGrant_thenCreditsOnceIdempotent() {
        Long userId = 700L;
        Long memberId = seedMember(userId);
        // 지갑은 가입 시점에 생성된다(#298). 이 테스트는 가입 경로를 타지 않으므로 직접 만든다.
        coinService.createWallet(userId);
        for (int slot = 1; slot <= 5; slot++) {
            seedUpload(memberId, 1, slot);
        }

        uploadRewardService.grantIfDayCompleted(memberId, 1);

        assertThat(coinWalletReader.getByUserId(userId).rewardBalance()).isEqualTo(5);
        assertThat(dailyUploadRewardJpaRepository.existsByRoomMemberIdAndDayNumber(memberId, 1)).isTrue();

        // 멱등: 재호출해도 잔액 그대로
        uploadRewardService.grantIfDayCompleted(memberId, 1);
        assertThat(coinWalletReader.getByUserId(userId).rewardBalance()).isEqualTo(5);
    }

    @Test
    @DisplayName("Day1 슬롯을 4개만 채우면(미완주) 보상이 지급되지 않는다")
    void givenDay1Incomplete_whenGrant_thenNoReward() {
        Long userId = 701L;
        Long memberId = seedMember(userId);
        for (int slot = 1; slot <= 4; slot++) {
            seedUpload(memberId, 1, slot);
        }

        uploadRewardService.grantIfDayCompleted(memberId, 1);

        assertThat(dailyUploadRewardJpaRepository.existsByRoomMemberIdAndDayNumber(memberId, 1)).isFalse();
        assertThat(coinWalletReader.findByUserId(userId)).isEmpty();
    }
}
