package com.kasi.musiclibrary.dto;

import com.kasi.musiclibrary.entity.Track;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Flattened on purpose. The library view shows one row per track with its album and artist
 * inline, and nesting them would make the client walk possibly-null objects for every cell.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrackResponse(
        UUID id,
        String title,
        String albumTitle,
        String artistName,
        Integer trackNumber,
        Integer discNumber,
        Integer durationMs,
        Integer releaseYear,

        @Schema(description = """
                Fields on this track that a person set by hand. Automated enrichment must leave
                these alone. Absent when nothing has been edited.
                """,
                example = "[\"title\", \"artistName\"]")
        List<String> userEditedFields) {

    public static TrackResponse from(Track track) {
        return from(track, List.of());
    }

    public static TrackResponse from(Track track, List<String> userEditedFields) {
        var album = track.getAlbum();
        var artist = album == null ? null : album.getAlbumArtist();
        return new TrackResponse(
                track.getId(),
                track.getTitle(),
                album == null ? null : album.getTitle(),
                artist == null ? null : artist.getName(),
                track.getTrackNumber(),
                track.getDiscNumber(),
                track.getDurationMs(),
                album == null ? null : album.getReleaseYear(),
                userEditedFields.isEmpty() ? null : userEditedFields);
    }
}
