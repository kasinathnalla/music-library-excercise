package com.kasi.musiclibrary.exception;

/**
 * Raised both when no playlist has that id and when one does but belongs to someone else.
 *
 * <p>The two cases are deliberately indistinguishable (D22). Answering 403 for the second would
 * confirm that the id exists, letting a prober map the shape of another user's library one guess
 * at a time. 404 is also the literally true answer to what was asked: show me <em>my</em> playlist
 * with this id -- there isn't one.
 */
public class PlaylistNotFoundException extends RuntimeException {

    public PlaylistNotFoundException() {
        super("No such playlist");
    }
}
