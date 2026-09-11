package com.kasi.musiclibrary.dto;

public record ParsedTags(
        String title,
        String artist,
        String album,
        String albumArtist,
        Integer releaseYear,
        Integer trackNumber,
        Integer discNumber,
        Integer durationMs) {
}
