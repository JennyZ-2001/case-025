package com.example.util;

import org.springframework.web.multipart.MultipartFile;
import cn.hutool.core.io.IoUtil;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Common file utility methods used by the application.
 * - importExcelAndCheck: check excel magic bytes for XLS/XLSX
 * - uploadImage: validate image by magic bytes/size and write to disk
 * - validateImage: helper to validate stream image header and size
 */
public class FileUtils {

    private static final byte[] XLS_MAGIC = new byte[]{(byte)0xD0, (byte)0xCF, 0x11, (byte)0xE0, (byte)0xA1, (byte)0xB1, 0x1A, (byte)0xE1};

    /**
     * Check MultipartFile is an Excel file (.xls or .xlsx) by reading the first 8 bytes.
     * This method will mark/read/reset the input stream when possible.
     */
    public static boolean importExcelAndCheck(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return false;
        }
        try (InputStream in = file.getInputStream();
             BufferedInputStream bin = new BufferedInputStream(in)) {
            bin.mark(8);
            byte[] head = new byte[8];
            int read = IoUtil.read(bin, head);
            bin.reset();
            if (read < 4) {
                return false;
            }
            // Check XLS (OLE Compound File)
            if (read >= 8) {
                boolean isXls = true;
                for (int i = 0; i < 8; i++) {
                    if (head[i] != XLS_MAGIC[i]) {
                        isXls = false;
                        break;
                    }
                }
                if (isXls) {
                    return true;
                }
            }
            // Check XLSX (zip PK..)
            if ((head[0] == 0x50 && head[1] == 0x4B) || // PK
                (head[0] == 'P' && head[1] == 'K')) {
                return true;
            }
            return false;
        }
    }

    /**
     * Upload an image after validating its magic bytes and max size.
     * The image will be written to the provided path with the provided name.
     */
    public static Path uploadImage(MultipartFile file, String dirPath, int maxSize, String name) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is empty");
        }
        try (InputStream in = file.getInputStream();
             BufferedInputStream bin = new BufferedInputStream(in)) {
            validateImage(bin, file, maxSize);
            Path dir = Path.of(dirPath);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            Path target = dir.resolve(name);
            // As we've consumed some bytes during validation, ensure the stream is reset by validateImage
            // and then copy from the stream
            try (InputStream toCopy = bin) {
                Files.copy(toCopy, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        }
    }

    /**
     * Validate image format by checking the stream's header (magic bytes) and size.
     * This method will mark the stream, read up to 8 bytes, then reset it.
     */
    public static void validateImage(BufferedInputStream stream, MultipartFile file, int maxSize) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is empty");
        }
        if (maxSize > 0 && file.getSize() > maxSize) {
            throw new IllegalArgumentException("file size exceeds limit");
        }
        stream.mark(16);
        byte[] head = new byte[8];
        int r = IoUtil.read(stream, head);
        stream.reset();
        if (r < 4) {
            throw new IllegalArgumentException("not an image or too small");
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (head[0] == (byte)0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47) {
            return;
        }
        // JPEG: FF D8 FF
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return;
        }
        // GIF: 'G','I','F','8'
        if (head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8') {
            return;
        }
        // BMP: 'B','M'
        if (head[0] == 'B' && head[1] == 'M') {
            return;
        }
        throw new IllegalArgumentException("unsupported image format");
    }
}
