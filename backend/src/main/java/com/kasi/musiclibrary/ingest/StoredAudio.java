package com.kasi.musiclibrary.ingest;

public record StoredAudio(String relativePath, long size, String contentHash) {
}
