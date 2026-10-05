package org.son.haruharu.admin.domain.room.business.dto;

public record AdminRoomSelectionResponse(
        Person chooser,
        Person selected,
        boolean submitted,
        boolean mutual,
        boolean matched
) {
    public record Person(long userId, String name) {}
}
