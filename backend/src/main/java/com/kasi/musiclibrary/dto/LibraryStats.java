package com.kasi.musiclibrary.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Headline counts for the library")
public record LibraryStats(
        @Schema(description = "Total tracks", example = "6") long trackCount,
        @Schema(description = "Distinct albums", example = "2") long albumCount,
        @Schema(description = "Distinct artists", example = "2") long artistCount,
        @Schema(description = "Combined duration of every track, in milliseconds") long totalDurationMs) {
}
