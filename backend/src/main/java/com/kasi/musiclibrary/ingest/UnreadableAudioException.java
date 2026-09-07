package com.kasi.musiclibrary.ingest;

public class UnreadableAudioException extends RuntimeException {
    public UnreadableAudioException(String message, Throwable cause) {
        super(message, cause);
    }
}
