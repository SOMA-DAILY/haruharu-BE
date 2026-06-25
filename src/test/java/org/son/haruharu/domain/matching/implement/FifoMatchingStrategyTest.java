package org.son.haruharu.domain.matching.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class FifoMatchingStrategyTest {

    private final FifoMatchingStrategy strategy = new FifoMatchingStrategy();

    private List<MatchingQueue> queues(String gender, int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> new MatchingQueue(
                UUID.randomUUID(), UUID.randomUUID(), gender,
                MatchingQueueStatus.QUEUED, LocalDateTime.now().plusSeconds(i)))
            .toList();
    }

    @Test
    @DisplayName("남3·여3이면 1개 방으로 묶이고 각 성별 3명씩 구성된다")
    void given3Males3Females_whenMatch_thenOneGroup() {
        List<MatchGroup> groups = strategy.match(queues("MALE", 3), queues("FEMALE", 3));

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).males()).hasSize(3);
        assertThat(groups.get(0).females()).hasSize(3);
    }

    @Test
    @DisplayName("남6·여6이면 2개 방으로 묶인다")
    void given6Males6Females_whenMatch_thenTwoGroups() {
        List<MatchGroup> groups = strategy.match(queues("MALE", 6), queues("FEMALE", 6));

        assertThat(groups).hasSize(2);
    }

    @Test
    @DisplayName("여성이 3명 미만이면 방이 만들어지지 않는다")
    void givenFemalesUnderThree_whenMatch_thenNoGroup() {
        List<MatchGroup> groups = strategy.match(queues("MALE", 5), queues("FEMALE", 2));

        assertThat(groups).isEmpty();
    }

    @Test
    @DisplayName("남4·여3이면 1개 방만 만들어지고 남은 1명은 이월된다")
    void given4Males3Females_whenMatch_thenOneGroupAndLeftover() {
        List<MatchingQueue> males = queues("MALE", 4);
        List<MatchGroup> groups = strategy.match(males, queues("FEMALE", 3));

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).males()).hasSize(3);
    }
}
