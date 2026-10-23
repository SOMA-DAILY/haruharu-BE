package org.son.haruharu.domain.chat.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.chat.implement.exception.InvalidChatPhotoException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.assertj.core.api.Assertions.*;

class ChatPhotoFileTest {
    private byte[] image(String format, int width, int height) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    @Test @DisplayName("실제 JPG·PNG의 원본 비율과 크기를 확인한다")
    void givenValidPhotos_whenValidate_thenReturnsActualDimensions() throws Exception {
        assertThat(ChatPhotoFile.validate(image("png", 120, 80), "image/png"))
                .isEqualTo(new ChatPhotoFile("image/png", "png", 120, 80));
        assertThat(ChatPhotoFile.validate(image("jpg", 80, 120), "image/jpeg").height()).isEqualTo(120);
    }

    @Test @DisplayName("확장자·선언 형식만 사진인 파일과 손상된 파일은 거절한다")
    void givenMismatchedOrCorruptBytes_whenValidate_thenRejects() throws Exception {
        byte[] png = image("png", 20, 20);
        assertThatThrownBy(() -> ChatPhotoFile.validate(png, "image/jpeg")).isInstanceOf(InvalidChatPhotoException.class);
        assertThatThrownBy(() -> ChatPhotoFile.validate(new byte[]{1, 2}, "image/png")).isInstanceOf(InvalidChatPhotoException.class);
    }

    @Test @DisplayName("빈 파일과 10MB 초과 파일을 거절한다")
    void givenEmptyOrOversize_whenValidate_thenRejects() {
        assertThatThrownBy(() -> ChatPhotoFile.validate(null, "image/png")).isInstanceOf(InvalidChatPhotoException.class);
        assertThatThrownBy(() -> ChatPhotoFile.validate(new byte[10 * 1024 * 1024 + 1], "image/png"))
                .isInstanceOf(InvalidChatPhotoException.class);
    }
    @Test @DisplayName("압축된 사진 메타데이터는 펼치지 않아 작은 파일의 메모리 폭증을 막는다")
    void givenCompressedMetadata_whenValidate_thenDoesNotExpandText() throws Exception {
        byte[] original = image("png", 1, 1);
        var compressed = new ByteArrayOutputStream();
        try (var deflater = new java.util.zip.DeflaterOutputStream(compressed)) {
            deflater.write(new byte[512 * 1024]);
        }
        var payload = new ByteArrayOutputStream();
        payload.write(new byte[]{'n', 0, 0}); // keyword, separator, compression method
        payload.write(compressed.toByteArray());
        var bytes = new ByteArrayOutputStream();
        bytes.write(original, 0, 33); // PNG signature + IHDR
        for (int i = 0; i < 16; i++) {
            byte[] type = new byte[]{'z','T','X','t'}, data = payload.toByteArray();
            var out = new java.io.DataOutputStream(bytes);
            out.writeInt(data.length); out.write(type); out.write(data);
            var crc = new java.util.zip.CRC32(); crc.update(type); crc.update(data); out.writeInt((int) crc.getValue());
        }
        bytes.write(original, 33, original.length - 33);
        ChatPhotoFile.validate(original, "image/png"); // decoder warmup
        var bean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        ChatPhotoFile validated = ChatPhotoFile.validate(bytes.toByteArray(), "image/png");
        long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before;
        assertThat(validated.width()).isEqualTo(1);
        assertThat(allocated).isLessThan(2_000_000);
    }
}
