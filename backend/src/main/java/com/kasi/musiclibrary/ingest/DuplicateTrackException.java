package com.kasi.musiclibrary.ingest;

import java.util.UUID;

public class DuplicateTrackException extends RuntimeException {

    private final UUID existingTrackId;

    public DuplicateTrackException(UUID existingTrackId) {
        super("This audio is already in the library");
        this.existingTrackId = existingTrackId;
    }

    public UUID getExistingTrackId() {
        return existingTrackId;
    }
}
