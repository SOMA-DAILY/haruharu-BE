package org.son.haruharu.admin.domain.midparticipation.implement;
import java.util.List;
public interface ParticipationCandidateRepository {
    List<ParticipationCandidate> find(String keyword,String gender,int offset,int limit);
    long count(String keyword,String gender);
}
