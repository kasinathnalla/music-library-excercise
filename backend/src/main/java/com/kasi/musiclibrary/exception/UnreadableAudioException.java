package com.kasi.musiclibrary.exception;

public class UnreadableAudioException extends RuntimeException {
    public UnreadableAudioException(String message, Throwable cause) {
        super(message, cause);
    }
}
