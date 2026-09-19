package org.son.haruharu.admin.domain.room.business;

import org.son.haruharu.admin.domain.room.business.dto.AdminRoomSelectionResponse;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomSelectionResponse.Person;
import org.son.haruharu.admin.domain.room.implement.AdminRoomReader;
import org.son.haruharu.admin.domain.room.implement.AdminRoomSelectionReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdminRoomSelectionService {
    private final AdminRoomReader rooms;
    private final AdminRoomSelectionReader selections;

    public AdminRoomSelectionService(AdminRoomReader rooms, AdminRoomSelectionReader selections) {
        this.rooms = rooms;
        this.selections = selections;
    }

    // 방의 전체 참여자를 기준으로 최종 선택 제출 / 상호 선택 / 매칭 결과를 반환
    public List<AdminRoomSelectionResponse> getSelections(Long roomId) {
        rooms.requireExists(roomId);
        return selections.list(roomId).stream()
                .map(row -> new AdminRoomSelectionResponse(toPerson(row.chooser()), toPerson(row.selected()),
                        row.submitted(), row.mutual(), row.matched()))
                .toList();
    }

    private Person toPerson(AdminRoomSelectionReader.Person person) {
        return person == null ? null : new Person(person.userId(), person.name());
    }
}
