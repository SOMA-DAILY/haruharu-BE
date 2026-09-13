package org.son.haruharu.domain.question.implement;

import java.time.LocalDateTime;

public record QuestionWindow(String phase, LocalDateTime opensAt, LocalDateTime closesAt) {
    public static QuestionWindow at(
            String roomStatus, LocalDateTime startsAt, LocalDateTime closesAt, LocalDateTime now) {
        LocalDateTime opensAt = startsAt == null ? null : startsAt.plusHours(24);
        String phase;
        if ("CANCELLED".equals(roomStatus)
                || "ENDED".equals(roomStatus)
                || "SELECTING".equals(roomStatus)) phase = "CLOSED";
        else if (opensAt == null || closesAt == null || now.isBefore(opensAt)) phase = "LOCKED";
        else phase = "ACTIVE".equals(roomStatus) && now.isBefore(closesAt) ? "OPEN" : "CLOSED";
        return new QuestionWindow(phase, opensAt, closesAt);
    }
}
