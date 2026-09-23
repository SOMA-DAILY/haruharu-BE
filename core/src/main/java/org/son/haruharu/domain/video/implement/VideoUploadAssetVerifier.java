package org.son.haruharu.domain.video.implement;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.son.haruharu.domain.video.implement.exception.VideoUploadIncompleteException;
@Component
@RequiredArgsConstructor
public class VideoUploadAssetVerifier {
    private final S3StoragePort storage;
    public String contentType(String variant) {
        return contentType(variant, null);
    }
    public String contentType(String variant, String requested) {
        String fallback = switch (variant) {
            case "viewer", "feed" -> "video/mp4";
            case "thumbnail" -> "image/jpeg";
            default -> throw new VideoUploadIncompleteException();
        };
        String type = requested == null ? fallback : requested;
        if (!allowedContentType(variant, type)) throw new VideoUploadIncompleteException();
        return type;
    }
    public boolean ready(VideoUploadSession session,String variant) {
        return valid(storage.head(session.key(variant)),variant);
    }
    public void promote(VideoUploadSession session) {
        var variants = List.of("viewer","feed","thumbnail");
        var metadata = variants.stream().map(variant -> {
            var meta = storage.head(session.key(variant));
            if (!valid(meta,variant) || meta.etag()==null) throw new VideoUploadIncompleteException();
            return meta;
        }).toList();
        try {
            for (int index = 0; index < variants.size(); index++) {
                var variant = variants.get(index);
                storage.promote(session.key(variant),session.publishedKey(variant),metadata.get(index).etag());
            }
        } catch (RuntimeException failure) {
            try {
                storage.deleteObjects(variants.stream().map(session::publishedKey).toList());
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }
    private boolean valid(S3StoragePort.ObjectMetadata metadata,String variant) {
        long max=switch (variant) { case "viewer" -> 10_000_000; case "feed" -> 5_000_000; case "thumbnail" -> 2_000_000; default -> throw new VideoUploadIncompleteException(); };
        return metadata!=null && metadata.bytes()>0 && metadata.bytes()<=max && allowedContentType(variant,metadata.contentType());
    }
    private boolean allowedContentType(String variant,String type) {
        return switch (variant) {
            case "viewer" -> "video/mp4".equals(type) || "video/quicktime".equals(type);
            case "feed" -> "video/mp4".equals(type);
            case "thumbnail" -> "image/jpeg".equals(type);
            default -> throw new VideoUploadIncompleteException();
        };
    }
}
