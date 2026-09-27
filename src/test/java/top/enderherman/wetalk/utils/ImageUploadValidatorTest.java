package top.enderherman.wetalk.utils;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import top.enderherman.wetalk.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageUploadValidatorTest {
    @Test
    void enforcesTheConfiguredByteLimitForVideoCovers() {
        byte[] pngSignature = new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a };
        MockMultipartFile cover = new MockMultipartFile("cover", "clip-cover.png", "image/png", pngSignature);

        assertDoesNotThrow(() -> ImageUploadValidator.validate(cover, pngSignature.length));
        assertThrows(BusinessException.class, () -> ImageUploadValidator.validate(cover, pngSignature.length - 1));
    }
}
