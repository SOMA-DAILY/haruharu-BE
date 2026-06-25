package org.son.haruharu.domain.matching.implement;

import java.util.List;

/**
 * 대기 큐에서 방을 구성하는 전략. 현재 구현은 FIFO 하나뿐이며,
 * 향후 가치관/프로필 기반 매칭, 어드민 수동 매칭 등으로 교체 가능한 이음새다.
 */
public interface MatchingStrategy {

    List<MatchGroup> match(List<MatchingQueue> males, List<MatchingQueue> females);
}
