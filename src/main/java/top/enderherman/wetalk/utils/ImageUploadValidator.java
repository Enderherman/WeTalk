package top.enderherman.wetalk.utils;

import org.springframework.web.multipart.MultipartFile;
import top.enderherman.wetalk.common.ResponseCodeEnum;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.exception.BusinessException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public final class ImageUploadValidator {
    private ImageUploadValidator() {}

    public static void validate(MultipartFile file) {
        if (file == null) return;
        if (file.isEmpty() || file.getSize() > 10 * Constants.FILE_SIZE_MB) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        String fileName = file.getOriginalFilename();
        String contentType = file.getContentType();
        if (fileName == null || contentType == null) throw new BusinessException(ResponseCodeEnum.CODE_600);
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        String expectedType = switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            default -> null;
        };
        if (expectedType == null || !expectedType.equalsIgnoreCase(contentType)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (!matchesImageSignature(extension, file)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
    }

    private static boolean matchesImageSignature(String extension, MultipartFile file) {
        byte[] header;
        try (InputStream input = file.getInputStream()) {
            header = input.readNBytes(12);
        } catch (IOException exception) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        return switch (extension) {
            case "png" -> header.length >= 8
                    && (header[0] & 0xff) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                    && header[4] == 0x0d && header[5] == 0x0a && header[6] == 0x1a && header[7] == 0x0a;
            case "jpg", "jpeg" -> header.length >= 3
                    && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff;
            case "gif" -> header.length >= 6
                    && header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8'
                    && (header[4] == '7' || header[4] == '9') && header[5] == 'a';
            case "bmp" -> header.length >= 2 && header[0] == 'B' && header[1] == 'M';
            case "webp" -> header.length >= 12
                    && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default -> false;
        };
    }
}
