package org.son.haruharu.domain.matching.implement;

import java.util.List;
import java.util.stream.Stream;

/** 매칭 전략이 묶어낸 한 방의 구성(남성 그룹 + 여성 그룹). */
public record MatchGroup(
        List<MatchingQueue> males,
        List<MatchingQueue> females
) {
    public List<MatchingQueue> all() {
        return Stream.concat(males.stream(), females.stream()).toList();
    }
}
