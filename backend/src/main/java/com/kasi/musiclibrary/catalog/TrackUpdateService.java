package com.kasi.musiclibrary.catalog;

import com.kasi.musiclibrary.provenance.ProvenanceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Applies hand edits to a track.
 *
 * <p>Editing a track's album or artist re-points that track rather than renaming the existing
 * rows. "This track is on a different album" and "this album is misnamed" are different
 * intentions, and a per-track edit means the first. Renaming an album or artist across the
 * library is a separate operation on that entity.
 *
 * <p>Every change is written to {@link ProvenanceService} so a later automated source cannot
 * silently undo it.
 */
@Service
public class TrackUpdateService {

    private final TrackRepository tracks;
    private final AlbumRepository albums;
    private final ArtistRepository artists;
    private final ProvenanceService provenance;

    public TrackUpdateService(TrackRepository tracks, AlbumRepository albums,
                              ArtistRepository artists, ProvenanceService provenance) {
        this.tracks = tracks;
        this.albums = albums;
        this.artists = artists;
        this.provenance = provenance;
    }

    public record Edit(String title, String albumTitle, String artistName,
                       Integer trackNumber, Integer discNumber, Integer releaseYear) {
    }

    @Transactional
    public Optional<Track> update(UUID trackId, Edit edit) {
        Optional<Track> found = tracks.findByIdWithAlbum(trackId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Track track = found.get();
        Album previousAlbum = track.getAlbum();

        if (edit.title() != null) {
            track.setTitle(edit.title().trim());
            provenance.recordUserEdit(ProvenanceService.TRACK, trackId, "title");
        }
        if (edit.trackNumber() != null) {
            track.setTrackNumber(edit.trackNumber());
            provenance.recordUserEdit(ProvenanceService.TRACK, trackId, "trackNumber");
        }
        if (edit.discNumber() != null) {
            track.setDiscNumber(edit.discNumber());
            provenance.recordUserEdit(ProvenanceService.TRACK, trackId, "discNumber");
        }

        if (edit.albumTitle() != null || edit.artistName() != null || edit.releaseYear() != null) {
            track.setAlbum(resolveAlbum(track, edit));
            if (edit.albumTitle() != null) {
                provenance.recordUserEdit(ProvenanceService.TRACK, trackId, "albumTitle");
            }
            if (edit.artistName() != null) {
                provenance.recordUserEdit(ProvenanceService.TRACK, trackId, "artistName");
            }
            if (edit.releaseYear() != null) {
                provenance.recordUserEdit(ProvenanceService.TRACK, trackId, "releaseYear");
            }
        }

        tracks.save(track);
        tracks.flush();

        cleanUpIfOrphaned(previousAlbum);
        return tracks.findByIdWithAlbum(trackId);
    }

    /**
     * Works out which album the track should now point at, treating whatever the edit did not
     * mention as unchanged from where the track currently sits.
     */
    private Album resolveAlbum(Track track, Edit edit) {
        Album current = track.getAlbum();
        String title = edit.albumTitle() != null
                ? edit.albumTitle().trim()
                : (current == null ? null : current.getTitle());

        // An empty album title is a request to detach, not to create an album named "".
        if (title == null || title.isBlank()) {
            return null;
        }

        Artist artist = resolveArtist(current, edit);
        Integer year = edit.releaseYear() != null
                ? edit.releaseYear()
                : (current == null ? null : current.getReleaseYear());

        return albums.findByTitleIgnoreCaseAndAlbumArtist(title, artist)
                .orElseGet(() -> albums.save(new Album(title, artist, year)));
    }

    private Artist resolveArtist(Album current, Edit edit) {
        if (edit.artistName() == null) {
            return current == null ? null : current.getAlbumArtist();
        }
        String name = edit.artistName().trim();
        if (name.isBlank()) {
            return null;
        }
        return artists.findByNameIgnoreCase(name)
                .orElseGet(() -> artists.save(new Artist(name)));
    }

    /** An album the track just left, with nothing else on it, should not linger. */
    private void cleanUpIfOrphaned(Album album) {
        if (album == null || tracks.countByAlbumId(album.getId()) > 0) {
            return;
        }
        Artist artist = album.getAlbumArtist();
        albums.delete(album);
        albums.flush();

        if (artist != null && albums.countByAlbumArtistId(artist.getId()) == 0) {
            artists.delete(artist);
        }
    }
}
