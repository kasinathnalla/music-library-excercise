package com.kasi.musiclibrary.service;

import com.kasi.musiclibrary.entity.Album;
import com.kasi.musiclibrary.entity.Artist;
import com.kasi.musiclibrary.entity.Track;
import com.kasi.musiclibrary.repository.AlbumRepository;
import com.kasi.musiclibrary.repository.ArtistRepository;
import com.kasi.musiclibrary.repository.TrackRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.UUID;

/**
 * Removes a track and anything left dangling behind it.
 *
 * <p>Deleting the last track of an album would otherwise leave an empty album, and in turn an
 * artist credited on nothing. Those rows are invisible in the track list but inflate the library
 * counts and reappear during ingest reconciliation, so they are cleaned up here.
 */
@Service
public class TrackDeletionService {

    private static final Logger log = LoggerFactory.getLogger(TrackDeletionService.class);

    private final TrackRepository tracks;
    private final AlbumRepository albums;
    private final ArtistRepository artists;
    private final AudioFileStore fileStore;
    private final ProvenanceService provenance;
    private final PlaylistService playlists;

    public TrackDeletionService(TrackRepository tracks, AlbumRepository albums,
                                ArtistRepository artists, AudioFileStore fileStore,
                                ProvenanceService provenance, PlaylistService playlists) {
        this.tracks = tracks;
        this.albums = albums;
        this.artists = artists;
        this.fileStore = fileStore;
        this.provenance = provenance;
        this.playlists = playlists;
    }

    /**
     * @return true if a track was deleted, false if there was no such track
     */
    @Transactional
    public boolean delete(UUID trackId) {
        Optional<Track> found = tracks.findByIdWithAlbum(trackId);
        if (found.isEmpty()) {
            return false;
        }
        Track track = found.get();
        Album album = track.getAlbum();
        String filePath = track.getFilePath();

        provenance.forget(ProvenanceService.TRACK, trackId);
        // Before the row goes. The foreign key would remove the playlist items either way, but
        // only doing it through JPA leaves the surviving positions contiguous.
        playlists.removeTrackEverywhere(trackId);
        tracks.delete(track);
        tracks.flush();

        cleanUpOrphans(album);
        deleteFile(filePath);
        return true;
    }

    private void cleanUpOrphans(Album album) {
        if (album == null) {
            return;
        }
        if (tracks.countByAlbumId(album.getId()) > 0) {
            return;
        }
        Artist artist = album.getAlbumArtist();
        albums.delete(album);
        albums.flush();

        if (artist != null && albums.countByAlbumArtistId(artist.getId()) == 0) {
            artists.delete(artist);
        }
    }

    /**
     * Best effort, and deliberately after the row is gone. A file that cannot be removed
     * leaves unreferenced bytes on disk, which is recoverable; failing the request after
     * the database change would be worse.
     */
    private void deleteFile(String relativePath) {
        try {
            Files.deleteIfExists(fileStore.resolve(relativePath));
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Deleted track {} but could not remove its file: {}", relativePath, e.getMessage());
        }
    }
}
