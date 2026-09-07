package com.kasi.musiclibrary.api;

import com.kasi.musiclibrary.catalog.Track;
import com.kasi.musiclibrary.catalog.TrackRepository;
import com.kasi.musiclibrary.ingest.AudioFileStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.Path;
import java.util.UUID;

@RestController
@Tag(name = "Playback", description = "Serving audio bytes to a player")
public class StreamController {

    private final TrackRepository tracks;
    private final AudioFileStore fileStore;

    public StreamController(TrackRepository tracks, AudioFileStore fileStore) {
        this.tracks = tracks;
        this.fileStore = fileStore;
    }

    @Operation(
            summary = "Stream a track's audio",
            description = """
                    Serves the audio with Accept-Ranges set, so a browser can seek rather than
                    only playing from the start. A Range header yields 206 Partial Content with a
                    Content-Range covering the requested window.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The whole file"),
            @ApiResponse(responseCode = "206", description = "The requested byte range"),
            @ApiResponse(responseCode = "404", description = "No such track, or its audio is missing from storage")
    })
    @GetMapping("/api/tracks/{id}/stream")
    public ResponseEntity<Resource> stream(@PathVariable UUID id) {
        Track track = tracks.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such track"));

        Path file = fileStore.resolve(track.getFilePath());
        Resource resource = new FileSystemResource(file);
        if (!resource.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio is missing from storage");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(track.getContentType()))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(resource);
    }
}
