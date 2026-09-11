package com.kasi.musiclibrary.controller;

import com.kasi.musiclibrary.dto.AddItemRequest;
import com.kasi.musiclibrary.dto.CreatePlaylistRequest;
import com.kasi.musiclibrary.dto.PlaylistResponse;
import com.kasi.musiclibrary.dto.PlaylistSummaryResponse;
import com.kasi.musiclibrary.dto.RenamePlaylistRequest;
import com.kasi.musiclibrary.dto.ReorderRequest;
import com.kasi.musiclibrary.security.AppUserPrincipal;
import com.kasi.musiclibrary.service.PlaylistService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Playlists, always the caller's own.
 *
 * <p>No matcher for these paths was added to {@code SecurityConfig}, and that is a decision rather
 * than an omission: {@code anyRequest().authenticated()} already covers them, and both roles should
 * be able to keep playlists. What the filter chain cannot express is whether a given playlist is
 * <em>yours</em>, so every method here passes {@code principal.id()} into {@link PlaylistService},
 * which scopes every query by it (D22).
 *
 * <p>Someone else's playlist is 404, never 403. A 403 would confirm the id exists.
 */
@RestController
@RequestMapping("/api/playlists")
@Tag(name = "Playlists", description = "Your own ordered arrangements of the library")
public class PlaylistController {

    private final PlaylistService playlists;

    public PlaylistController(PlaylistService playlists) {
        this.playlists = playlists;
    }

    @Operation(summary = "Your playlists", description = "Most recently changed first.")
    @GetMapping
    public List<PlaylistSummaryResponse> list(@AuthenticationPrincipal AppUserPrincipal principal) {
        return playlists.listFor(principal.id()).stream()
                .map(PlaylistSummaryResponse::from)
                .toList();
    }

    @Operation(summary = "Create a playlist")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "409", description = "You already have one with that name")
    })
    @PostMapping
    public ResponseEntity<PlaylistResponse> create(@AuthenticationPrincipal AppUserPrincipal principal,
                                                   @Valid @RequestBody CreatePlaylistRequest request) {
        var created = playlists.create(principal.id(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(PlaylistResponse.from(created));
    }

    @Operation(summary = "One playlist, with its ordered contents")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No playlist of yours has that id"))
    @GetMapping("/{id}")
    public PlaylistResponse get(@AuthenticationPrincipal AppUserPrincipal principal,
                                @PathVariable UUID id) {
        return PlaylistResponse.from(playlists.getFor(principal.id(), id));
    }

    @Operation(summary = "Rename a playlist")
    @PatchMapping("/{id}")
    public PlaylistResponse rename(@AuthenticationPrincipal AppUserPrincipal principal,
                                   @PathVariable UUID id,
                                   @Valid @RequestBody RenamePlaylistRequest request) {
        return PlaylistResponse.from(playlists.rename(principal.id(), id, request.name()));
    }

    @Operation(summary = "Delete a playlist", description = "The tracks themselves are untouched.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @PathVariable UUID id) {
        playlists.delete(principal.id(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Add a track to the end",
            description = "The same track may appear more than once in a playlist.")
    @PostMapping("/{id}/items")
    public PlaylistResponse addItem(@AuthenticationPrincipal AppUserPrincipal principal,
                                    @PathVariable UUID id,
                                    @Valid @RequestBody AddItemRequest request) {
        return PlaylistResponse.from(playlists.addTrack(principal.id(), id, request.trackId()));
    }

    @Operation(summary = "Remove one entry",
            description = "Positions close up, so they stay contiguous from zero.")
    @DeleteMapping("/{id}/items/{itemId}")
    public PlaylistResponse removeItem(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @PathVariable UUID id,
                                       @PathVariable UUID itemId) {
        return PlaylistResponse.from(playlists.removeItem(principal.id(), id, itemId));
    }

    @Operation(summary = "Reorder",
            description = """
                    Send the complete new order as a list of item ids. Sending the whole order
                    rather than a single move makes this idempotent and means two tabs cannot
                    interleave into an order neither asked for.

                    A list that is not a permutation of the playlist's current items is refused
                    with 400, so a reorder can never quietly drop an entry.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reordered"),
            @ApiResponse(responseCode = "400", description = "Not a permutation of the current items")
    })
    @PutMapping("/{id}/items")
    public PlaylistResponse reorder(@AuthenticationPrincipal AppUserPrincipal principal,
                                    @PathVariable UUID id,
                                    @Valid @RequestBody ReorderRequest request) {
        return PlaylistResponse.from(playlists.reorder(principal.id(), id, request.itemIds()));
    }
}
