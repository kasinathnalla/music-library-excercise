package com.kasi.musiclibrary.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** The body returned for every handled error, so clients have one shape to parse. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Error body returned for handled failures")
public record ErrorResponse(
        @Schema(description = "Human-readable explanation", example = "This audio is already in the library")
        String message,

        @Schema(description = "Set only on a duplicate upload: the track that already holds these bytes")
        UUID existingTrackId) {

    public ErrorResponse(String message) {
        this(message, null);
    }
}
