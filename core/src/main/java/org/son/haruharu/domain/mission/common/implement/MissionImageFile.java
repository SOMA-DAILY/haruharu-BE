package org.son.haruharu.domain.mission.common.implement;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionImageException;

/** 업로드한 바이트를 실제로 디코딩해 확인한 사진. 선언한 형식과 내용이 다르면 거절한다. */
public record MissionImageFile(String contentType, String extension, int width, int height) {
    static final int MAX_BYTES = 10 * 1024 * 1024;
    static final long MAX_PIXELS = 25_000_000L;
    private static final int DECODE_SUBSAMPLING = 8;

    public static MissionImageFile validate(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES
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
                if (!actual.equals(contentType) || width < 1 || height < 1 || (long) width * height > MAX_PIXELS)
                    throw new InvalidMissionImageException();
                // 끝까지 디코딩되는지만 확인한다. 원본 크기 래스터를 메모리에 올리지 않도록 줄여서 읽는다.
                var param = reader.getDefaultReadParam();
                param.setSourceSubsampling(DECODE_SUBSAMPLING, DECODE_SUBSAMPLING, 0, 0);
                if (reader.read(0, param) == null) throw new InvalidMissionImageException();
                return new MissionImageFile(actual, actual.equals("image/png") ? "png" : "jpg", width, height);
            } finally {
                reader.dispose();
            }
        } catch (InvalidMissionImageException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            // 손상된 파일은 디코더가 여러 런타임 예외로 알린다. 모두 잘못된 사진으로 본다.
            throw new InvalidMissionImageException();
        }
    }
}
