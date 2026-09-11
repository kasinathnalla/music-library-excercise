package com.kasi.musiclibrary.exception;

/** Raised when an item id is not in the playlist it was asked for. */
public class PlaylistItemNotFoundException extends RuntimeException {

    public PlaylistItemNotFoundException() {
        super("No such item in this playlist");
    }
}
