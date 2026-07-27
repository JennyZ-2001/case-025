package com.example.util;

import org.springframework.web.multipart.MultipartFile;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Common file utility methods used by the application.
 * - importExcelAndCheck: check excel magic bytes for XLS/XLSX
 * - uploadImage: validate image by magic bytes/size and write to disk
 * - validateImage: helper to validate stream image header and size (now returns suffix)
 *
 * Feature-branch additions:
 * - uploadImageWithoutThumb: upload only original image (no thumbnail)
 * - getThumbnailFile / thumbnailExists / getThumbBase64IfExists: thumbnail helpers
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
            // validateImage was void before; after change it returns suffix but callers that ignore the return will still work
            validateImage(bin, file, maxSize);
            Path dir = Path.of(dirPath);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            Path target = dir.resolve(name);
            // After validateImage resets stream, copy remaining content to target
            try (InputStream toCopy = bin) {
                Files.copy(toCopy, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        }
    }

    /**
     * Validate image format by checking the stream's header (magic bytes) and size.
     * This method will mark the stream, read up to 8 bytes, then reset it.
     * It returns a file suffix (including the leading dot), e.g. ".png", ".jpg".
     */
    public static String validateImage(BufferedInputStream stream, MultipartFile file, int maxSize) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is empty");
        }
        if (maxSize > 0 && file.getSize() > maxSize) {
            throw new IllegalArgumentException("file size exceeds limit");
        }
        // mark/reset happens here
        stream.mark(8);
        byte[] head = new byte[8];
        int r = IoUtil.read(stream, head);
        stream.reset();
        if (r < 4) {
            throw new IllegalArgumentException("not an image or too small");
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((head[0] & 0xFF) == 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47) {
            return ".png";
        }
        // JPEG: FF D8 FF
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return ".jpg";
        }
        // GIF: 'G','I','F','8'
        if (head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8') {
            return ".gif";
        }
        // BMP: 'B','M'
        if (head[0] == 'B' && head[1] == 'M') {
            return ".bmp";
        }
        throw new IllegalArgumentException("unsupported image format");
    }

    /**
     * Feature branch addition:
     * Upload an image but do NOT generate a thumbnail. Keeps the original image only.
     * Uses BufferedInputStream and validateImage (which performs mark/reset), then writes the stream to disk.
     *
     * IMPORTANT: this method must NOT call mark/reset itself.
     *
     * Returns the saved filename (fileNameNotSuffix + suffix).
     */
    public static String uploadImageWithoutThumb(MultipartFile imageFile, String filePath, int maxSize, String fileNameNotSuffix) throws IOException {
        if (imageFile == null || imageFile.isEmpty()) {
            throw new IllegalArgumentException("imageFile is empty");
        }
        try (InputStream in = imageFile.getInputStream();
             BufferedInputStream bin = new BufferedInputStream(in)) {

            // validateImage performs mark/reset internally and returns suffix
            String suffix = validateImage(bin, imageFile, maxSize);

            // Validate provided fileNameNotSuffix (no path separators, no traversal, basic allowed chars)
            if (fileNameNotSuffix == null || fileNameNotSuffix.isBlank()) {
                throw new IllegalArgumentException("fileNameNotSuffix is required");
            }
            if (fileNameNotSuffix.contains("..") || fileNameNotSuffix.contains("/") || fileNameNotSuffix.contains("\\") ) {
                throw new IllegalArgumentException("invalid fileNameNotSuffix");
            }

            Path dir = Path.of(filePath);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            String fileName = fileNameNotSuffix + suffix;
            File targetFile = dir.resolve(fileName).toFile();

            // Write stream directly to target (stream has been reset by validateImage)
            FileUtil.writeFromStream(bin, targetFile);

            return fileName;
        }
    }

    /**
     * Return the expected thumbnail file Path for a given original file.
     * Strategy: place thumbnail alongside original with suffix ".thumb.jpg".
     * Adjust this function if you prefer a separate thumbs/ directory or other naming.
     */
    public static Path getThumbnailFile(Path originalFile) {
        Objects.requireNonNull(originalFile, "originalFile");
        String filename = originalFile.getFileName().toString();
        String thumbName = filename + ".thumb.jpg";
        return originalFile.getParent().resolve(thumbName);
    }

    /**
     * Check whether thumbnail exists for given original file.
     */
    public static boolean thumbnailExists(Path originalFile) {
        Path thumb = getThumbnailFile(originalFile);
        return Files.exists(thumb) && Files.isRegularFile(thumb);
    }

    /**
     * If thumbnail exists, return its Base64 representation (data part only).
     * Returns null if not exists.
     */
    public static String getThumbBase64IfExists(Path originalFile) throws IOException {
        Path thumb = getThumbnailFile(originalFile);
        if (!Files.exists(thumb) || !Files.isRegularFile(thumb)) {
            return null;
        }
        byte[] bytes = Files.readAllBytes(thumb);
        return Base64.encode(bytes);
    }
}
