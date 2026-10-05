package org.son.haruharu.domain.chat.implement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.son.haruharu.domain.mission.implement.MissionImageReader;
import org.son.haruharu.domain.mission.business.dto.MissionImageResponse;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
@Component @RequiredArgsConstructor
public class ChatMissionPresenter {
    private final MissionImageReader images;
    private final S3StoragePort storage;
    public ChatMission present(ChatMission mission) { return mission == null ? null : mission.withImage(view(mission.imageId())); }
    public java.util.function.UnaryOperator<ChatMission> prepare(java.util.List<ChatMission> missions) {
        var photos = images.getAll(missions.stream().filter(java.util.Objects::nonNull).map(ChatMission::imageId).toList());
        var signed = photos.values().stream().collect(java.util.stream.Collectors.toMap(image -> image.id(), image ->
                new MissionImageResponse(image.id(),storage.createPresignedImageGetUrl(storage.publicBaseUrl()+image.objectKey()),image.width(),image.height())));
        return mission -> mission == null ? null : mission.withImage(signed.get(mission.imageId()));
    }
    private MissionImageResponse view(Long id) {
        if (id == null) return null;
        var image = images.get(id);
        return new MissionImageResponse(id, storage.createPresignedImageGetUrl(storage.publicBaseUrl()+image.objectKey()), image.width(), image.height());
    }
}
