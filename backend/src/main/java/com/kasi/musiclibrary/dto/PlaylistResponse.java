package com.kasi.musiclibrary.dto;

import com.kasi.musiclibrary.entity.Playlist;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A playlist with its ordered contents.
 *
 * <p>Never a serialized {@link Playlist}: that would be a JPA entity on the wire, which AGENTS.md
 * rules out, and its items reach through to tracks and albums that may not be loaded.
 */
public record PlaylistResponse(UUID id, String name, Instant updatedAt,
                               List<PlaylistItemResponse> items) {

    public static PlaylistResponse from(Playlist playlist) {
        return new PlaylistResponse(
                playlist.getId(),
                playlist.getName(),
                playlist.getUpdatedAt(),
                playlist.getItems().stream().map(PlaylistItemResponse::from).toList());
    }
}
