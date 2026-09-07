package com.kasi.musiclibrary.ingest;

import com.kasi.musiclibrary.catalog.Album;
import com.kasi.musiclibrary.catalog.AlbumRepository;
import com.kasi.musiclibrary.catalog.Artist;
import com.kasi.musiclibrary.catalog.ArtistRepository;
import com.kasi.musiclibrary.catalog.Track;
import com.kasi.musiclibrary.catalog.TrackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.InputStream;
import java.nio.file.Path;

@Service
public class IngestService {

    private final AudioFileStore fileStore;
    private final AudioTagReader tagReader;
    private final TrackRepository tracks;
    private final AlbumRepository albums;
    private final ArtistRepository artists;

    public IngestService(AudioFileStore fileStore, AudioTagReader tagReader,
                         TrackRepository tracks, AlbumRepository albums,
                         ArtistRepository artists) {
        this.fileStore = fileStore;
        this.tagReader = tagReader;
        this.tracks = tracks;
        this.albums = albums;
        this.artists = artists;
    }

    @Transactional
    public IngestResult ingest(InputStream audio, String originalFilename) {
        StoredAudio stored = fileStore.store(audio, originalFilename);

        tracks.findByContentHash(stored.contentHash()).ifPresent(existing -> {
            throw new DuplicateTrackException(existing.getId());
        });

        Path onDisk = fileStore.resolve(stored.relativePath());
        ParsedTags tags = tagReader.read(onDisk);

        Album album = resolveAlbum(tags);
        String title = tags.title() != null ? tags.title() : titleFromFilename(originalFilename);

        Track track = tracks.save(new Track(
                title,
                album,
                tags.trackNumber(),
                tags.discNumber(),
                tags.durationMs(),
                stored.relativePath(),
                stored.size(),
                contentTypeFor(stored.relativePath()),
                stored.contentHash()));

        return new IngestResult(track.getId(), track.getTitle());
    }

    private Album resolveAlbum(ParsedTags tags) {
        if (tags.album() == null) {
            return null;
        }
        Artist albumArtist = resolveArtist(
                tags.albumArtist() != null ? tags.albumArtist() : tags.artist());

        return albums.findByTitleIgnoreCaseAndAlbumArtist(tags.album(), albumArtist)
                .orElseGet(() -> albums.save(new Album(tags.album(), albumArtist, tags.releaseYear())));
    }

    private Artist resolveArtist(String name) {
        if (name == null) {
            return null;
        }
        return artists.findByNameIgnoreCase(name)
                .orElseGet(() -> artists.save(new Artist(name)));
    }

    private String titleFromFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "Untitled";
        }
        String base = Path.of(filename).getFileName().toString();
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }

    private String contentTypeFor(String relativePath) {
        String lower = relativePath.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (lower.endsWith(".flac")) {
            return "audio/flac";
        }
        if (lower.endsWith(".m4a") || lower.endsWith(".mp4")) {
            return "audio/mp4";
        }
        if (lower.endsWith(".ogg") || lower.endsWith(".oga")) {
            return "audio/ogg";
        }
        if (lower.endsWith(".wav")) {
            return "audio/wav";
        }
        return "application/octet-stream";
    }
}
