package com.kasi.musiclibrary.api;

import com.kasi.musiclibrary.ingest.DuplicateTrackException;
import com.kasi.musiclibrary.ingest.UnreadableAudioException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DuplicateTrackException.class)
    public ResponseEntity<ErrorResponse> duplicate(DuplicateTrackException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.getMessage(), e.getExistingTrackId()));
    }

    @ExceptionHandler(UnreadableAudioException.class)
    public ResponseEntity<ErrorResponse> unreadable(UnreadableAudioException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorResponse("That file is larger than the upload limit"));
    }
}
