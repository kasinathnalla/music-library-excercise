package com.kasi.musiclibrary.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddItemRequest(
        @NotNull(message = "A track id is required")
        UUID trackId) {
}
