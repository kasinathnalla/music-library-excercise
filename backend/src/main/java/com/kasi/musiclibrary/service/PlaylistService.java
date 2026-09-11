package com.kasi.musiclibrary.service;

import com.kasi.musiclibrary.entity.Playlist;
import com.kasi.musiclibrary.entity.Track;
import com.kasi.musiclibrary.exception.DuplicatePlaylistNameException;
import com.kasi.musiclibrary.exception.PlaylistItemNotFoundException;
import com.kasi.musiclibrary.exception.PlaylistNotFoundException;
import com.kasi.musiclibrary.exception.TrackNotFoundException;
import com.kasi.musiclibrary.repository.PlaylistRepository;
import com.kasi.musiclibrary.repository.PlaylistSummary;
import com.kasi.musiclibrary.repository.TrackRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Playlists, always seen through the eyes of one owner.
 *
 * <p><strong>Every method takes {@code ownerId} first, and there is no overload that omits it.</strong>
 * That is the whole security model of this package (D22): a caller cannot reach someone else's
 * playlist by forgetting an argument, because the argument is not optional.
 *
 * <p>There is deliberately no {@code @PreAuthorize} here. Per AGENTS.md and D15 the authorization
 * matrix lives in {@code SecurityConfig}, and it still does -- {@code /api/playlists/**} is covered
 * by {@code anyRequest().authenticated()}, which is correct, because both roles may keep playlists.
 * What {@code SecurityConfig} cannot express is whether a particular <em>row</em> is yours, and
 * that is what this class is for. The two are not in competition.
 */
@Service
@Transactional
public class PlaylistService {

    private final PlaylistRepository playlists;
    private final TrackRepository tracks;

    public PlaylistService(PlaylistRepository playlists, TrackRepository tracks) {
        this.playlists = playlists;
        this.tracks = tracks;
    }

    @Transactional(readOnly = true)
    public List<PlaylistSummary> listFor(UUID ownerId) {
        return playlists.findSummariesByOwnerId(ownerId);
    }

    @Transactional(readOnly = true)
    public Playlist getFor(UUID ownerId, UUID playlistId) {
        return owned(ownerId, playlistId);
    }

    public Playlist create(UUID ownerId, String name) {
        String trimmed = name.trim();
        // Checked, not caught. See existsByOwnerIdAndNameIgnoreCase for why catching the
        // constraint violation in here would turn a 409 into a 500.
        if (playlists.existsByOwnerIdAndNameIgnoreCase(ownerId, trimmed)) {
            throw new DuplicatePlaylistNameException(trimmed);
        }
        return playlists.saveAndFlush(new Playlist(ownerId, trimmed));
    }

    public Playlist rename(UUID ownerId, UUID playlistId, String newName) {
        String trimmed = newName.trim();
        Playlist playlist = owned(ownerId, playlistId);
        if (!playlist.getName().equalsIgnoreCase(trimmed)
                && playlists.existsByOwnerIdAndNameIgnoreCase(ownerId, trimmed)) {
            throw new DuplicatePlaylistNameException(trimmed);
        }
        playlist.rename(trimmed);
        playlists.flush();
        return playlist;
    }

    public void delete(UUID ownerId, UUID playlistId) {
        playlists.delete(playlists.findByIdAndOwnerId(playlistId, ownerId)
                .orElseThrow(PlaylistNotFoundException::new));
    }

    public Playlist addTrack(UUID ownerId, UUID playlistId, UUID trackId) {
        Playlist playlist = owned(ownerId, playlistId);
        // Fetched with its album and artist so the response can be serialized after the session
        // closes. getReferenceById would hand back a proxy that blows up during serialization.
        Track track = tracks.findByIdWithAlbum(trackId).orElseThrow(TrackNotFoundException::new);
        playlist.add(track);
        playlists.flush();
        return playlist;
    }

    public Playlist removeItem(UUID ownerId, UUID playlistId, UUID itemId) {
        Playlist playlist = owned(ownerId, playlistId);
        if (!playlist.removeItem(itemId)) {
            throw new PlaylistItemNotFoundException();
        }
        playlists.flush();
        return playlist;
    }

    public Playlist reorder(UUID ownerId, UUID playlistId, List<UUID> itemIdsInOrder) {
        Playlist playlist = owned(ownerId, playlistId);
        playlist.reorder(itemIdsInOrder);
        playlists.flush();
        return playlist;
    }

    /**
     * Removes a track from every playlist holding it, closing the gaps it leaves.
     *
     * <p>Called by {@code TrackDeletionService} before the track row goes, inside the same
     * transaction. Going through JPA rather than relying on the foreign key cascade keeps
     * Hibernate's view of the world true and is what lets the surviving positions be renumbered.
     *
     * <p>Not owner-scoped, and that is correct: an admin deleting a track affects every listener's
     * playlists. That is a library-wide consequence of a library-wide act, not one user reaching
     * into another's data (D22 is about request paths).
     */
    public void removeTrackEverywhere(UUID trackId) {
        for (UUID playlistId : playlists.findIdsContainingTrack(trackId)) {
            playlists.findByIdWithItemsForMaintenance(playlistId)
                    .ifPresent(playlist -> playlist.removeItemsForTrack(trackId));
        }
        playlists.flush();
    }

    /** The one way into a playlist, and it needs both ids. A stranger's is indistinguishable from absent. */
    private Playlist owned(UUID ownerId, UUID playlistId) {
        return playlists.findByIdAndOwnerIdWithItems(playlistId, ownerId)
                .orElseThrow(PlaylistNotFoundException::new);
    }
}
