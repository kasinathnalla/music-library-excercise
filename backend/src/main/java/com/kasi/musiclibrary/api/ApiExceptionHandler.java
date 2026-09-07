package com.kasi.musiclibrary.api;

import com.kasi.musiclibrary.ingest.DuplicateTrackException;
import com.kasi.musiclibrary.ingest.UnreadableAudioException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

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

    /**
     * Every {@code @Valid} failure on a request body -- registration's included -- lands here
     * instead of Spring's default problem-detail shape, so a client only ever has to parse one
     * error body. The message names every field that failed, not just the first one Spring
     * happened to collect, so a caller who left three fields blank finds out about all three at
     * once rather than being sent back three times in a row.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> invalid(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (first, second) -> first,
                        LinkedHashMap::new));

        String summary = fieldErrors.values().stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining("; "));

        return ResponseEntity.badRequest().body(new ValidationErrorResponse(summary, fieldErrors));
    }
}
