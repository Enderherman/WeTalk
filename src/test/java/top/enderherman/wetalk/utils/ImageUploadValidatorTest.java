package top.enderherman.wetalk.utils;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import top.enderherman.wetalk.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageUploadValidatorTest {
    @Test
    void chatAcceptsMjpegJpegAliasesButStillChecksMimeSignatureSizeAndProfileScope() {
        byte[] jpeg = new byte[] { (byte) 0xff, (byte) 0xd8, (byte) 0xff, 0 };
        for (String mime : new String[]{"image/jpeg", "video/x-motion-jpeg", "image/x-mjpeg", "video/mjpeg"}) {
            MockMultipartFile image = new MockMultipartFile("file", "photo.mjpeg", mime, jpeg);
            assertDoesNotThrow(() -> ImageUploadValidator.validateChatImage(image, 4));
            assertThrows(BusinessException.class, () -> ImageUploadValidator.validateChatImage(image, 3));
            assertThrows(BusinessException.class, () -> ImageUploadValidator.validate(image));
        }
        assertThrows(BusinessException.class, () -> ImageUploadValidator.validateChatImage(
                new MockMultipartFile("file", "photo.mjpeg", "text/plain", jpeg), 4));
        assertThrows(BusinessException.class, () -> ImageUploadValidator.validateChatImage(
                new MockMultipartFile("file", "photo.mjpeg", "image/jpeg", new byte[]{1, 2, 3}), 4));
        assertThrows(BusinessException.class, () -> ImageUploadValidator.validateChatImage(
                new MockMultipartFile("file", "photo.png", "", jpeg), 4));
    }

    @Test
    void enforcesTheConfiguredByteLimitForVideoCovers() {
        byte[] pngSignature = new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a };
        MockMultipartFile cover = new MockMultipartFile("cover", "clip-cover.png", "image/png", pngSignature);

        assertDoesNotThrow(() -> ImageUploadValidator.validate(cover, pngSignature.length));
        assertThrows(BusinessException.class, () -> ImageUploadValidator.validate(cover, pngSignature.length - 1));
    }
}
