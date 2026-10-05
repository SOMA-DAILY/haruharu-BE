package org.son.haruharu.domain.mission.implement;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.son.haruharu.domain.mission.implement.exception.InvalidMissionImageException;
public record MissionImageFile(String contentType, String extension, int width, int height) {
    public static MissionImageFile validate(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0 || bytes.length > 10 * 1024 * 1024
                || !("image/png".equals(contentType) || "image/jpeg".equals(contentType))) throw new InvalidMissionImageException();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new InvalidMissionImageException();
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName();
                String actual = "png".equalsIgnoreCase(format) ? "image/png" : "JPEG".equalsIgnoreCase(format) ? "image/jpeg" : "";
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (!actual.equals(contentType) || width < 1 || height < 1 || (long) width * height > 25_000_000L
                        || reader.read(0) == null) throw new InvalidMissionImageException();
                return new MissionImageFile(actual, actual.equals("image/png") ? "png" : "jpg", width, height);
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException exception) { throw new InvalidMissionImageException(); }
    }
}
