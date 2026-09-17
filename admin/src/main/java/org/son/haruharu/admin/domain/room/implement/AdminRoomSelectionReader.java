package org.son.haruharu.admin.domain.room.implement;

import org.son.haruharu.admin.domain.room.implement.AdminRoomReader;
import org.son.haruharu.domain.room.implement.FinalSelectionReader;
import org.son.haruharu.domain.room.implement.MatchResultReader;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AdminRoomSelectionReader {
    private final AdminRoomReader rooms;
    private final FinalSelectionReader selections;
    private final MatchResultReader matches;

    public AdminRoomSelectionReader(AdminRoomReader rooms, FinalSelectionReader selections, MatchResultReader matches) {
        this.rooms = rooms;
        this.selections = selections;
        this.matches = matches;
    }

    public record Person(long userId, String name) {}
    public record Row(Person chooser, Person selected, boolean submitted, boolean mutual, boolean matched) {}

    public List<Row> list(Long roomId) {
        var members = rooms.membersOf(roomId);
        var byId = members.stream().collect(Collectors.toMap(RoomMember::id, Function.identity()));
        var choices = selections.findByRoomMemberIds(members.stream().map(RoomMember::id).toList()).stream()
                .collect(Collectors.toMap(s -> s.roomMemberId(), Function.identity()));
        var matchCache = new java.util.HashMap<String, Boolean>();
        return members.stream().sorted(java.util.Comparator.comparing(RoomMember::userId)).map(member -> {
            var choice = choices.get(member.id());
            var target = choice == null ? null : byId.get(choice.selectedMemberId());
            var reverse = target == null ? null : choices.get(target.id());
            boolean mutual = target != null && !member.id().equals(target.id()) && reverse != null
                    && member.id().equals(reverse.selectedMemberId());
            boolean matched = mutual && matchCache.computeIfAbsent(
                    Math.min(member.id(), target.id()) + ":" + Math.max(member.id(), target.id()),
                    key -> matches.isMatched(member.id(), target.id()));
            return new Row(person(member), target == null ? null : person(target), choice != null, mutual, matched);
        }).toList();
    }

    private Person person(RoomMember member) {
        return new Person(member.userId(), member.pseudonym() == null || member.pseudonym().isBlank() ? "가명 없음" : member.pseudonym());
    }
}
