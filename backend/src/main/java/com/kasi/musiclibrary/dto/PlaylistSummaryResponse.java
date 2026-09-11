package com.kasi.musiclibrary.dto;

import com.kasi.musiclibrary.repository.PlaylistSummary;

import java.time.Instant;
import java.util.UUID;

/** A playlist without its contents, for the list view. */
public record PlaylistSummaryResponse(UUID id, String name, int trackCount, Instant updatedAt) {

    public static PlaylistSummaryResponse from(PlaylistSummary summary) {
        return new PlaylistSummaryResponse(summary.getId(), summary.getName(),
                summary.getTrackCount(), summary.getUpdatedAt());
    }
}
