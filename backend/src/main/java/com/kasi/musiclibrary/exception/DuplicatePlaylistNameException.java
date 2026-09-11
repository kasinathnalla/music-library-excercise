package com.kasi.musiclibrary.exception;

/**
 * Raised when an owner already has a playlist with that name, case-insensitively.
 *
 * <p>The unique index {@code playlist_owner_name_key} is the actual guard; this turns its
 * violation into something a client can show, the same way {@code AuthController.register}
 * handles a taken username.
 */
public class DuplicatePlaylistNameException extends RuntimeException {

    public DuplicatePlaylistNameException(String name) {
        super("You already have a playlist called \"" + name + "\"");
    }
}
