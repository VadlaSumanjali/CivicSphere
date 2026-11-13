package com.civicsphere.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class FileUploadUtil {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif"
    );

    private FileUploadUtil() {
    }

    public static String saveImage(MultipartFile file, String subFolder, String baseUploadDir, long maxBytes)
            throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("Uploaded image exceeds the 5 MB limit.");
        }

        String submittedName = file.getOriginalFilename();
        String ext = extractExtension(submittedName);
        String contentType = file.getContentType() != null
                ? file.getContentType().toLowerCase(Locale.ROOT)
                : "";

        if (!ALLOWED_EXTENSIONS.contains(ext) || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Only JPG, PNG, WEBP, and GIF image files are supported.");
        }

        byte[] header = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.read(header);
        }
        if (!matchesImageMagicBytes(header, read, ext)) {
            throw new IllegalArgumentException("Invalid image file signature. Upload a genuine image file.");
        }

        String safeFolder = ("resolutions".equalsIgnoreCase(subFolder)) ? "resolutions" : "complaints";
        String storedFilename = UUID.randomUUID().toString() + "." + ext;
        Path baseDir = Paths.get(baseUploadDir).toAbsolutePath().normalize();
        Path targetDir = baseDir.resolve(safeFolder).normalize();
        Files.createDirectories(targetDir);

        Path targetFile = targetDir.resolve(storedFilename).normalize();
        if (!targetFile.startsWith(targetDir)) {
            throw new IllegalArgumentException("Invalid upload file path.");
        }

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return safeFolder + "/" + storedFilename;
    }

    public static Path resolveSafeUploadPath(String baseUploadDir, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        String normalized = relativePath.replace("\\", "/").replaceAll("^/+", "");
        if (!normalized.matches("^(complaints|resolutions)/[0-9a-fA-F\\-]{8,64}\\.(jpg|jpeg|png|webp|gif)$")) {
            return null;
        }
        Path baseDir = Paths.get(baseUploadDir).toAbsolutePath().normalize();
        Path resolved = baseDir.resolve(normalized).normalize();
        if (!resolved.startsWith(baseDir) || !Files.exists(resolved) || !Files.isRegularFile(resolved)) {
            return null;
        }
        return resolved;
    }

    public static Path resolveSafeUploadPath(String relativePath) {
        return resolveSafeUploadPath("civicsphere-uploads", relativePath);
    }

    public static void deleteQuietly(String baseUploadDir, String relativePath) {
        try {
            Path safe = resolveSafeUploadPath(baseUploadDir, relativePath);
            if (safe != null) {
                Files.deleteIfExists(safe);
            }
        } catch (Exception ignored) {
        }
    }

    public static String detectContentType(String filename) {
        String ext = extractExtension(filename);
        return switch (ext) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> "image/jpeg";
        };
    }

    private static String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT).trim();
    }

    private static boolean matchesImageMagicBytes(byte[] h, int len, String ext) {
        if (len < 4) {
            return false;
        }
        if ("jpg".equals(ext) || "jpeg".equals(ext)) {
            return (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
        }
        if ("png".equals(ext)) {
            return (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
        }
        if ("gif".equals(ext)) {
            return h[0] == 'G' && h[1] == 'I' && h[2] == 'F';
        }
        if ("webp".equals(ext)) {
            return len >= 12
                    && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                    && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
        }
        return false;
    }
}
