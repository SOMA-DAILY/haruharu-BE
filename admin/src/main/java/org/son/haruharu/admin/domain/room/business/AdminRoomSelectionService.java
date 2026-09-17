package org.son.haruharu.admin.domain.room.business;

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
    public record Person(long userId, String name) {}
    public record Row(Person chooser, Person selected, boolean submitted, boolean mutual, boolean matched) {}
    public List<Row> list(Long roomId) {
        rooms.requireExists(roomId);
        return selections.list(roomId).stream().map(row -> new Row(person(row.chooser()), person(row.selected()),
                row.submitted(), row.mutual(), row.matched())).toList();
    }
    private Person person(AdminRoomSelectionReader.Person person) {
        return person == null ? null : new Person(person.userId(), person.name());
    }
}
