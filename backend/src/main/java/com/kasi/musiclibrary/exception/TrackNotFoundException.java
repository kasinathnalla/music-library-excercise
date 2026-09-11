package com.kasi.musiclibrary.exception;

/**
 * Raised when a track id being added to a playlist is not in the library.
 *
 * <p>Lives in this package rather than {@code catalog} because it describes a failed playlist
 * operation, not a catalog one -- nothing in catalog needs to signal this.
 */
public class TrackNotFoundException extends RuntimeException {

    public TrackNotFoundException() {
        super("No such track");
    }
}
