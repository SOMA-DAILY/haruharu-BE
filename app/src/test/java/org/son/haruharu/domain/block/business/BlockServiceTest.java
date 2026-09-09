package org.son.haruharu.domain.block.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.block.business.command.CreateBlockCommand;
import org.son.haruharu.domain.block.business.dto.BlockResponse;
import org.son.haruharu.domain.block.business.dto.BlocksResponse;
import org.son.haruharu.domain.block.business.exception.BlockAlreadyExistsException;
import org.son.haruharu.domain.block.business.exception.CannotBlockSelfException;
import org.son.haruharu.domain.block.implement.Block;
import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.block.implement.BlockWriter;
import org.son.haruharu.domain.block.storage.exception.BlockNotFoundException;
import org.son.haruharu.domain.user.implement.UserReader;
import org.son.haruharu.domain.user.storage.exception.UserNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class BlockServiceTest {

    @Mock BlockReader blockReader;
    @Mock BlockWriter blockWriter;
    @Mock UserReader userReader;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-09T03:00:00Z"), ZoneOffset.UTC);

    BlockService blockService;

    private static final Long ME = 1L;
    private static final Long TARGET = 2L;

    @BeforeEach
    void setUp() {
        blockService = new BlockService(blockReader, blockWriter, userReader, clock);
    }

    @Test
    @DisplayName("유효한 대상을 차단하면 차단 관계가 저장되고 block_id가 반환된다")
    void givenValidTarget_whenBlock_thenSavesAndReturnsId() {
        given(blockReader.hasBlocked(ME, TARGET)).willReturn(false);
        given(blockWriter.save(any())).willReturn(new Block(7L, ME, TARGET, LocalDateTime.now(clock)));

        BlockResponse result = blockService.block(ME, new CreateBlockCommand(TARGET));

        assertThat(result.blockId()).isEqualTo(7L);
        ArgumentCaptor<Block> captor = ArgumentCaptor.forClass(Block.class);
        then(blockWriter).should().save(captor.capture());
        assertThat(captor.getValue().blockerUserId()).isEqualTo(ME);
        assertThat(captor.getValue().blockedUserId()).isEqualTo(TARGET);
        assertThat(captor.getValue().createdAt()).isEqualTo(LocalDateTime.now(clock));
    }

    @Test
    @DisplayName("자기 자신을 차단하면 CannotBlockSelfException이 발생한다")
    void givenSelf_whenBlock_thenThrowsCannotBlockSelf() {
        assertThatThrownBy(() -> blockService.block(ME, new CreateBlockCommand(ME)))
                .isInstanceOf(CannotBlockSelfException.class);
        then(blockWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 유저를 차단하면 UserNotFoundException이 발생한다")
    void givenUnknownTarget_whenBlock_thenThrowsUserNotFound() {
        given(userReader.getById(999L)).willThrow(new UserNotFoundException());

        assertThatThrownBy(() -> blockService.block(ME, new CreateBlockCommand(999L)))
                .isInstanceOf(UserNotFoundException.class);
        then(blockWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("이미 차단한 대상을 다시 차단하면 BlockAlreadyExistsException이 발생한다")
    void givenDuplicate_whenBlock_thenThrowsAlreadyExists() {
        given(blockReader.hasBlocked(ME, TARGET)).willReturn(true);

        assertThatThrownBy(() -> blockService.block(ME, new CreateBlockCommand(TARGET)))
                .isInstanceOf(BlockAlreadyExistsException.class);
        then(blockWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("차단을 해제하면 내가 건 차단 행이 삭제된다")
    void givenBlocked_whenUnblock_thenDeletes() {
        given(blockWriter.delete(ME, TARGET)).willReturn(1);

        blockService.unblock(ME, TARGET);

        then(blockWriter).should().delete(ME, TARGET);
    }

    @Test
    @DisplayName("차단하지 않은 대상을 해제하면 BlockNotFoundException이 발생한다")
    void givenNotBlocked_whenUnblock_thenThrowsNotFound() {
        given(blockWriter.delete(ME, TARGET)).willReturn(0);

        assertThatThrownBy(() -> blockService.unblock(ME, TARGET))
                .isInstanceOf(BlockNotFoundException.class);
    }

    @Test
    @DisplayName("내 차단 목록 조회 시 내가 차단한 userId 목록이 반환된다")
    void givenBlocks_whenGetMyBlocks_thenReturnsBlockedUserIds() {
        given(blockReader.getBlockedUserIds(ME)).willReturn(Set.of(2L, 3L));

        BlocksResponse result = blockService.getMyBlocks(ME);

        assertThat(result.blockedUserIds()).containsExactlyInAnyOrder(2L, 3L);
    }
}
