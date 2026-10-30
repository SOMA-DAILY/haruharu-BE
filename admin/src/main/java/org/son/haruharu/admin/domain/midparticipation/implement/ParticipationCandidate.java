package org.son.haruharu.admin.domain.midparticipation.implement;
import java.time.LocalDateTime;
public record ParticipationCandidate(Long userId,String nickname,String gender,long videoCount,LocalDateTime lastVideoUploadedAt) {}
