package org.son.haruharu.domain.matching.implement;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 신청 순서(FIFO)대로 남3·여3을 한 방으로 묶는다. 성비가 안 맞는 잔여 인원은 다음 배치로 이월된다. */
@Component
public class FifoMatchingStrategy implements MatchingStrategy {

    private static final int GENDER_GROUP_SIZE = 3;

    @Override
    public List<MatchGroup> match(List<MatchingQueue> males, List<MatchingQueue> females) {
        int groupCount = Math.min(males.size(), females.size()) / GENDER_GROUP_SIZE;
        List<MatchGroup> groups = new ArrayList<>();
        for (int i = 0; i < groupCount; i++) {
            int from = i * GENDER_GROUP_SIZE;
            int to = from + GENDER_GROUP_SIZE;
            groups.add(new MatchGroup(
                List.copyOf(males.subList(from, to)),
                List.copyOf(females.subList(from, to))
            ));
        }
        return groups;
    }
}
