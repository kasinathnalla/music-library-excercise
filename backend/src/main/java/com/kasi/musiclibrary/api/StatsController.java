package com.kasi.musiclibrary.api;

import com.kasi.musiclibrary.catalog.AlbumRepository;
import com.kasi.musiclibrary.catalog.ArtistRepository;
import com.kasi.musiclibrary.catalog.TrackRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Library", description = "Summary information about the whole library")
public class StatsController {

    private final TrackRepository tracks;
    private final AlbumRepository albums;
    private final ArtistRepository artists;

    public StatsController(TrackRepository tracks, AlbumRepository albums, ArtistRepository artists) {
        this.tracks = tracks;
        this.albums = albums;
        this.artists = artists;
    }

    @Operation(summary = "Headline counts for the library",
            description = "Backs the welcome screen so it reports real numbers rather than placeholders.")
    @GetMapping("/api/stats")
    public LibraryStats stats() {
        return new LibraryStats(
                tracks.count(),
                albums.count(),
                artists.count(),
                tracks.totalDurationMs());
    }
}
