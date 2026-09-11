package com.kasi.musiclibrary.service;

import com.kasi.musiclibrary.config.StorageProperties;
import com.kasi.musiclibrary.dto.StoredAudio;

import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class AudioFileStore {

    private final Path mediaRoot;

    public AudioFileStore(StorageProperties properties) {
        this.mediaRoot = Path.of(properties.mediaRoot()).toAbsolutePath().normalize();
    }

    public StoredAudio store(InputStream input, String originalFilename) {
        try {
            Files.createDirectories(mediaRoot);
            Path staging = Files.createTempFile(mediaRoot, "upload-", ".part");

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (DigestInputStream hashing = new DigestInputStream(input, digest)) {
                size = Files.copy(hashing, staging, StandardCopyOption.REPLACE_EXISTING);
            }

            String hash = HexFormat.of().formatHex(digest.digest());
            String relativePath = shardedPath(hash, extensionOf(originalFilename));
            Path destination = mediaRoot.resolve(relativePath);

            Files.createDirectories(destination.getParent());
            Files.move(staging, destination, StandardCopyOption.REPLACE_EXISTING);

            return new StoredAudio(relativePath, size, hash);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store " + originalFilename, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public Path resolve(String relativePath) {
        Path resolved = mediaRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(mediaRoot)) {
            throw new IllegalArgumentException("Path escapes the media root: " + relativePath);
        }
        return resolved;
    }

    private String shardedPath(String hash, String extension) {
        return hash.substring(0, 2) + "/" + hash.substring(2, 4) + "/" + hash + extension;
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String extension = filename.substring(dot).toLowerCase(Locale.ROOT);
        return extension.matches("\\.[a-z0-9]{1,5}") ? extension : "";
    }
}
