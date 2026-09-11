package com.kasi.musiclibrary.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * The complete new order, not a move (D23).
 *
 * <p>Sending the whole order makes the request idempotent and removes any need for conflict
 * resolution between two tabs reordering the same list. The server rejects a list that is not a
 * permutation of the playlist's current items, so this cannot quietly drop an entry.
 */
public record ReorderRequest(
        @NotNull(message = "The new order is required")
        List<UUID> itemIds) {
}
