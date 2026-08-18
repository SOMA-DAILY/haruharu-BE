package org.son.haruharu.admin.domain.mission.business;

import org.son.haruharu.admin.domain.mission.business.command.CreateMissionCommand;
import org.son.haruharu.admin.domain.mission.business.command.UpdateMissionCommand;
import org.son.haruharu.admin.domain.mission.business.dto.AdminMissionResponse;
import org.son.haruharu.admin.domain.mission.business.exception.AdminMissionDuplicateException;
import org.son.haruharu.admin.domain.mission.implement.AdminMissionReader;
import org.son.haruharu.admin.domain.mission.implement.AdminMissionWriter;
import org.son.haruharu.domain.mission.implement.Mission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 어드민 미션 카탈로그 관리(생성·목록·수정, 전부 SUPER 권한 — AdminSecurityConfig에서 제한).
 * 여기서 관리하는 활성 미션이 방 배정(FixedTwicePerDayAssigner)의 후보 풀이 된다.
 */
@Service
@Transactional(readOnly = true)
public class AdminMissionService {

    private final AdminMissionReader adminMissionReader;
    private final AdminMissionWriter adminMissionWriter;

    public AdminMissionService(AdminMissionReader adminMissionReader, AdminMissionWriter adminMissionWriter) {
        this.adminMissionReader = adminMissionReader;
        this.adminMissionWriter = adminMissionWriter;
    }

    public List<AdminMissionResponse> getMissions() {
        return adminMissionReader.findAll().stream().map(AdminMissionResponse::from).toList();
    }

    @Transactional
    public AdminMissionResponse create(CreateMissionCommand command) {
        if (adminMissionReader.existsByDescription(command.description())) {
            throw new AdminMissionDuplicateException();
        }
        Mission saved = adminMissionWriter.save(Mission.of(command.description(), command.points()));
        return AdminMissionResponse.from(saved);
    }

    @Transactional
    public AdminMissionResponse update(Long id, UpdateMissionCommand command) {
        Mission existing = adminMissionReader.getById(id);
        // 문구를 실제로 바꾸는 경우에만 중복 검사. 생성이 dup 문구를 막으므로 수정도 같은 불변식을 지킨다.
        // (자기 자신은 검사 대상이 아니므로 "문구가 달라질 때"만 확인 — 별도 exclude 쿼리가 필요 없다.)
        if (command.description() != null
                && !command.description().equals(existing.description())
                && adminMissionReader.existsByDescription(command.description())) {
            throw new AdminMissionDuplicateException();
        }
        // 부분 수정: 제공된 필드만 갱신.
        Mission updated = new Mission(
                existing.id(),
                command.description() != null ? command.description() : existing.description(),
                command.points() != null ? command.points() : existing.points(),
                command.active() != null ? command.active() : existing.isActive());
        return AdminMissionResponse.from(adminMissionWriter.save(updated));
    }
}
