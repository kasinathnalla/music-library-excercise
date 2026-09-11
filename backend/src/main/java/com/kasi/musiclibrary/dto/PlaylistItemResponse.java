package com.kasi.musiclibrary.dto;

import com.kasi.musiclibrary.entity.PlaylistItem;

import java.util.UUID;

/**
 * One entry in a playlist.
 *
 * <p>The track is nested rather than flattened, which is the opposite of {@link TrackResponse}'s
 * choice and for the same underlying reason: shape the wire format to how it is read. A library
 * row is a track and flattening spares the client a null walk per cell; a playlist entry is a
 * <em>position</em> that happens to hold a track, and the client needs {@code itemId} and
 * {@code position} as first-class things to remove and reorder by.
 */
public record PlaylistItemResponse(UUID itemId, int position, TrackResponse track) {

    public static PlaylistItemResponse from(PlaylistItem item) {
        return new PlaylistItemResponse(item.getId(), item.getPosition(),
                TrackResponse.from(item.getTrack()));
    }
}
