package org.son.haruharu.domain.mission.implement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.son.haruharu.domain.mission.implement.exception.InvalidMissionImageException;
@Component @RequiredArgsConstructor
public class MissionImageReader {
    private final MissionImageRepository repository;
    public MissionImage get(Long id) { return repository.findById(id).orElseThrow(InvalidMissionImageException::new); }
    public void requireRoom(Long id, Long roomId) { if (id != null && !get(id).roomId().equals(roomId)) throw new InvalidMissionImageException(); }
    public java.util.Map<Long,MissionImage> getAll(java.util.Collection<Long> ids) {
        var keys = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (keys.isEmpty()) return java.util.Map.of();
        return repository.findAllById(keys).stream().collect(java.util.stream.Collectors.toMap(MissionImage::id, image -> image));
    }
}
