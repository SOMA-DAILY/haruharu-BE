package org.son.haruharu.domain.replyrecommendation.implement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReplyDepthCalculatorTest {

    private static final long R = 100L;

    private static ReplyDepthMessage reply(long id, long to) {
        return new ReplyDepthMessage(id, to, false);
    }

    private static ReplyDepthMessage hidden(long id, long to) {
        return new ReplyDepthMessage(id, to, true);
    }

    @Test
    @DisplayName("답장 r → 상대 → 나 → 상대 는 depth 3, width 1")
    void givenLinearChain_thenDepthThree() {
        var result = ReplyDepthCalculator.calculate(R, List.of(reply(101, R), reply(102, 101), reply(103, 102)), 200);

        assertThat(result).isEqualTo(ReplyDepthResult.observed(3, 1));
    }

    @Test
    @DisplayName("분기가 있으면 가장 긴 경로, width 는 직접 답글 수")
    void givenBranches_thenLongestPathAndDirectWidth() {
        var result = ReplyDepthCalculator.calculate(R, List.of(
                reply(101, R), reply(102, R), reply(103, R),
                reply(104, 101),
                reply(105, 102), reply(106, 105), reply(107, 106),
                reply(108, 104)), 200);

        assertThat(result).isEqualTo(ReplyDepthResult.observed(4, 3));
    }

    @Test
    @DisplayName("답글이 없으면 미관측이 아니라 depth 0")
    void givenNoReplies_thenObservedZero() {
        var result = ReplyDepthCalculator.calculate(R, List.of(reply(101, 50)), 200);

        assertThat(result.status()).isEqualTo(ReplyDepthStatus.OBSERVED);
        assertThat(result.depth()).isZero();
        assertThat(result.width()).isZero();
    }

    @Test
    @DisplayName("기간 밖 메시지는 입력에 없으므로 세지 않는다(호출자가 기간으로 거른 결과만 센다)")
    void givenChainCutAtWindow_thenOnlyWindowCounted() {
        // 103(기간 밖)은 저장소가 돌려주지 않는다. 104 는 103 에 단 답글이라 r 와 이어지지 않는다.
        var result = ReplyDepthCalculator.calculate(R, List.of(reply(101, R), reply(102, 101), reply(104, 103)), 200);

        assertThat(result).isEqualTo(ReplyDepthResult.observed(2, 1));
    }

    @Test
    @DisplayName("끝에 달린 삭제 메시지는 세지 않는다")
    void givenHiddenLeaf_thenNotCounted() {
        var result = ReplyDepthCalculator.calculate(R, List.of(reply(101, R), hidden(102, 101), hidden(103, R)), 200);

        assertThat(result).isEqualTo(ReplyDepthResult.observed(1, 1));
    }

    @Test
    @DisplayName("삭제 메시지 아래로 답글이 이어지면 연결이 끊긴 것이라 미관측(depth null)")
    void givenHiddenMiddle_thenUnobserved() {
        var result = ReplyDepthCalculator.calculate(R, List.of(reply(101, R), hidden(102, 101), reply(103, 102)), 200);

        assertThat(result).isEqualTo(ReplyDepthResult.unobserved(ReplyDepthResult.CHAIN_BROKEN));
        assertThat(result.depth()).isNull();
        assertThat(result.width()).isNull();
    }

    @Test
    @DisplayName("경로가 상한을 넘으면 미관측")
    void givenPathOverLimit_thenUnobserved() {
        var result = ReplyDepthCalculator.calculate(R, List.of(reply(101, R), reply(102, 101), reply(103, 102)), 2);

        assertThat(result).isEqualTo(ReplyDepthResult.unobserved(ReplyDepthResult.DEPTH_LIMIT));
    }

    private static ReplyDepthMessage by(long id, long to, long sender, boolean hidden) {
        return new ReplyDepthMessage(id, to, hidden, sender);
    }

    @Test
    @DisplayName("영상 작성자가 답글 트리 안에서 답했으면 true, 트리 밖·숨겨진 가지에서만 말했으면 false")
    void givenOwnerMessages_thenOwnerRepliedOnlyInsideVisibleTree() {
        long owner = 7L;
        assertThat(ReplyDepthCalculator.ownerReplied(R,
                List.of(by(101, R, 2, false), by(102, 101, owner, false)), owner)).isTrue();
        assertThat(ReplyDepthCalculator.ownerReplied(R,
                List.of(by(101, R, 2, false), by(102, 50, owner, false)), owner)).isFalse();
        assertThat(ReplyDepthCalculator.ownerReplied(R,
                List.of(by(101, R, owner, true), by(102, 101, 2, false)), owner)).isFalse();
    }
}
