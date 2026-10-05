package org.son.haruharu.domain.mission.business;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.implement.*;
import org.son.haruharu.domain.mission.business.dto.MissionImageResponse;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class MissionImageService {
    private final MissionImageReader reader;
    private final MissionImageWriter writer;
    private final S3StoragePort storage;
    private final MissionImageUploader uploader;
    @Transactional public MissionImageResponse upload(Long roomId, Long adminId, byte[] bytes, String contentType) {
        var image = writer.save(uploader.upload(roomId,adminId,bytes,contentType));
        return view(image.id());
    }
    public MissionImageResponse view(Long id) {
        if (id == null) return null;
        var image = reader.get(id);
        return new MissionImageResponse(image.id(),storage.createPresignedImageGetUrl(storage.publicBaseUrl()+image.objectKey()),image.width(),image.height());
    }
    public java.util.Map<Long,MissionImageResponse> views(java.util.Collection<Long> ids) {
        return reader.getAll(ids).values().stream().collect(java.util.stream.Collectors.toMap(MissionImage::id, image ->
                new MissionImageResponse(image.id(),storage.createPresignedImageGetUrl(storage.publicBaseUrl()+image.objectKey()),image.width(),image.height())));
    }
}
