package org.son.haruharu.admin.domain.midparticipation.implement;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class ParticipationCandidateReader {
    private final ParticipationCandidateRepository repository;
    public List<ParticipationCandidate> find(String keyword,String gender,int offset,int limit) { return repository.find(keyword,gender,offset,limit); }
    public long count(String keyword,String gender) { return repository.count(keyword,gender); }
}
