package com.kasi.musiclibrary.exception;

/**
 * Raised when a submitted order is not a permutation of the playlist's current items.
 *
 * <p>Reordering sends the whole order (D23), which makes it idempotent but also means a caller
 * can send a list that silently drops an item. That would be data loss wearing the costume of a
 * sort, so it is refused rather than applied.
 */
public class InvalidReorderException extends RuntimeException {

    public InvalidReorderException(String message) {
        super(message);
    }
}
