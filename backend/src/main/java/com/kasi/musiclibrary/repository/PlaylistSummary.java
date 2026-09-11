package com.kasi.musiclibrary.repository;

import java.time.Instant;
import java.util.UUID;

/**
 * A playlist without its contents, for the list view.
 *
 * <p>A projection rather than the entity so that listing playlists is one query: loading each
 * playlist's items only to call {@code size()} on them would be a query per playlist for a number
 * the database can compute.
 */
public interface PlaylistSummary {

    UUID getId();

    String getName();

    Instant getUpdatedAt();

    int getTrackCount();
}
