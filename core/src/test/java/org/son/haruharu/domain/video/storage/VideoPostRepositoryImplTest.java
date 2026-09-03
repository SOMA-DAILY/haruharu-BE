package org.son.haruharu.domain.video.storage;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.video.implement.VideoPostRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class VideoPostRepositoryImplTest {

    @Mock VideoPostJpaRepository jpaRepository;
    private VideoPostRepository repository;

    @BeforeEach
    void setUp() {
        repository = new VideoPostRepositoryImpl(jpaRepository);
    }

    @Test
    void givenEmptyMemberIds_whenFindVisibleSlotStarts_thenReturnsEmptyWithoutJpaCall() {
        assertThat(repository.findVisibleSlotStarts(List.of(), List.of(LocalDateTime.now()))).isEmpty();

        then(jpaRepository).should(never()).findVisibleSlotStarts(org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void givenEmptySlotStarts_whenFindVisibleSlotStarts_thenReturnsEmptyWithoutJpaCall() {
        assertThat(repository.findVisibleSlotStarts(List.of(1L), List.of())).isEmpty();

        then(jpaRepository).should(never()).findVisibleSlotStarts(org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyList());
    }
}
