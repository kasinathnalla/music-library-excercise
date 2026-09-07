package com.kasi.musiclibrary.api;

import com.kasi.musiclibrary.catalog.TrackDeletionService;
import com.kasi.musiclibrary.catalog.TrackUpdateService;
import com.kasi.musiclibrary.catalog.TrackRepository;
import com.kasi.musiclibrary.ingest.IngestResult;
import com.kasi.musiclibrary.ingest.IngestService;
import com.kasi.musiclibrary.provenance.ProvenanceService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/tracks")
@Tag(name = "Tracks", description = "Ingest, browse, and search the library")
public class TrackController {

    private static final int MAX_PAGE_SIZE = 200;

    private final IngestService ingest;
    private final TrackRepository tracks;
    private final TrackDeletionService deletion;
    private final TrackUpdateService updates;
    private final ProvenanceService provenance;

    public TrackController(IngestService ingest, TrackRepository tracks,
                           TrackDeletionService deletion, TrackUpdateService updates,
                           ProvenanceService provenance) {
        this.ingest = ingest;
        this.tracks = tracks;
        this.deletion = deletion;
        this.updates = updates;
        this.provenance = provenance;
    }

    private TrackResponse withProvenance(com.kasi.musiclibrary.catalog.Track track) {
        return TrackResponse.from(track,
                provenance.userEditedFields(ProvenanceService.TRACK, track.getId()));
    }

    @Operation(
            summary = "Add an audio file to the library",
            description = """
                    Stores the bytes, reads embedded tags, and reconciles the artist and album
                    against existing rows so a second track from the same album reuses both.

                    Track identity is a SHA-256 of the file content, so re-uploading the same
                    audio under a different filename is a conflict rather than a second copy.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Added to the library"),
            @ApiResponse(responseCode = "400", description = "Empty, or not readable as audio",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "These exact bytes are already in the library",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "Larger than the upload limit",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<TrackResponse> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is empty");
        }
        try (var stream = file.getInputStream()) {
            IngestResult result = ingest.ingest(stream, file.getOriginalFilename());
            TrackResponse body = withProvenance(tracks.findByIdWithAlbum(result.trackId()).orElseThrow());
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Operation(
            summary = "List and search tracks",
            description = """
                    Returns a page of tracks ordered by title, case-insensitively.

                    The query matches track title, album title, and album artist name. Tracks with
                    no album are included; the joins are left joins for exactly that reason.

                    Page size is clamped to 200 so an unbounded request cannot be used to pull the
                    whole library in one call.
                    """)
    @GetMapping
    public TrackPage list(
            @Parameter(description = "Free-text match on title, album, or artist. Omit for everything.")
            @RequestParam(required = false) String query,
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Rows per page, clamped to 200")
            @RequestParam(defaultValue = "50") int size) {

        int effectiveSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        String effectiveQuery = (query == null || query.isBlank()) ? null : query.trim();

        var pageable = PageRequest.of(Math.max(page, 0), effectiveSize,
                Sort.by(Sort.Order.asc("title").ignoreCase()));

        return TrackPage.from(tracks.search(effectiveQuery, pageable));
    }

    @Operation(summary = "Fetch one track")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "404", description = "No track with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public TrackResponse get(@PathVariable UUID id) {
        return tracks.findByIdWithAlbum(id)
                .map(this::withProvenance)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such track"));
    }

    @Operation(
            summary = "Edit a track's metadata",
            description = """
                    A partial update: fields left out are unchanged.

                    Editing albumTitle or artistName moves this track to that album or artist,
                    matching an existing row before creating one. It does not rename the album or
                    artist across the library, because "this track is on a different album" and
                    "this album is misnamed" are different intentions. An empty string detaches
                    the association entirely.

                    An album left with no tracks is removed, as is an artist left with no albums.

                    Every edited field is recorded, and automated enrichment is required to leave
                    recorded fields alone. Edits change the library only; the tags inside your
                    audio files are not rewritten.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "400", description = "A supplied value is not valid",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No track with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}")
    public TrackResponse update(@PathVariable UUID id, @Valid @RequestBody TrackUpdateRequest request) {
        if (request.title() != null && request.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title cannot be blank");
        }
        var edit = new TrackUpdateService.Edit(
                request.title(), request.albumTitle(), request.artistName(),
                request.trackNumber(), request.discNumber(), request.releaseYear());

        return updates.update(id, edit)
                .map(this::withProvenance)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such track"));
    }

    @Operation(
            summary = "Remove a track from the library",
            description = """
                    Deletes the track, its stored audio, and anything left dangling: an album with
                    no remaining tracks is removed, and an artist credited on no remaining albums
                    goes with it.

                    This is permanent. There is no undo in this version.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "404", description = "No track with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (!deletion.delete(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such track");
        }
        return ResponseEntity.noContent().build();
    }
}
