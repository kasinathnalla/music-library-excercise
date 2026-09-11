package com.kasi.musiclibrary.dto;

public record StoredAudio(String relativePath, long size, String contentHash) {
}
