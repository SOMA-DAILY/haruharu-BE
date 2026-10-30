package org.son.haruharu.admin.domain.midparticipation.business;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.midparticipation.implement.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ParticipationCandidateService {
    private final ParticipationCandidateReader reader;
    public PageResponse<ParticipationCandidate> candidates(String keyword,String gender,int page,int size) {
        int p=Math.max(0,Math.min(page,1000000)),s=Math.max(1,Math.min(size,100));
        return PageResponse.of(reader.find(keyword,gender,p*s,s),p,s,reader.count(keyword,gender));
    }
}
