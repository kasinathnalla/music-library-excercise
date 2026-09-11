package com.kasi.musiclibrary.repository;

import com.kasi.musiclibrary.entity.Playlist;
import com.kasi.musiclibrary.entity.PlaylistItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaylistRepository extends JpaRepository<Playlist, UUID> {

    /**
     * Every finder here takes an owner id, and that is not an accident -- there is deliberately no
     * {@code findById} wrapper that omits it, so a caller cannot reach another user's playlist by
     * forgetting a parameter (D22).
     */
    @Query("""
            select p.id as id, p.name as name, p.updatedAt as updatedAt, size(p.items) as trackCount
            from Playlist p
            where p.ownerId = :ownerId
            order by p.updatedAt desc
            """)
    List<PlaylistSummary> findSummariesByOwnerId(@Param("ownerId") UUID ownerId);

    Optional<Playlist> findByIdAndOwnerId(UUID id, UUID ownerId);

    /**
     * Checked before inserting rather than catching the unique-index violation afterwards.
     *
     * <p>Catching {@code DataIntegrityViolationException} inside a transaction is a trap: the
     * transaction is already marked rollback-only by the time the exception is caught, so
     * translating it into a tidy 409 still blows up as an {@code UnexpectedRollbackException} at
     * commit -- a 500 where the client should have seen a conflict. {@code playlist_owner_name_key}
     * remains the real guard against a race; this is what makes the ordinary case answer correctly.
     */
    boolean existsByOwnerIdAndNameIgnoreCase(UUID ownerId, String name);

    /**
     * Loads a playlist with its items, their tracks, and each track's album and artist already
     * fetched.
     *
     * <p>Needed because open-in-view is off: a plain find returns lazy proxies, and the session is
     * closed by the time the response is serialized. Same reasoning as
     * {@code TrackRepository.findByIdWithAlbum}.
     *
     * <p><strong>Every join is a left join and that is load-bearing.</strong> A track with no album
     * is legitimate, and an inner join would silently drop it -- the defect
     * {@code TrackControllerTest.listIncludesTracksThatHaveNoAlbum} exists to catch, restated here
     * for playlists.
     */
    @Query("""
            select distinct p from Playlist p
            left join fetch p.items i
            left join fetch i.track t
            left join fetch t.album a
            left join fetch a.albumArtist
            where p.id = :id and p.ownerId = :ownerId
            """)
    Optional<Playlist> findByIdAndOwnerIdWithItems(@Param("id") UUID id,
                                                   @Param("ownerId") UUID ownerId);

    /**
     * The same fetch, not scoped to an owner. Used only when a track is deleted from the library,
     * which affects every listener who had it -- a library-wide consequence of a library-wide act,
     * not one user reaching into another's data. Do not call it from a request path.
     */
    @Query("""
            select distinct p from Playlist p
            left join fetch p.items i
            left join fetch i.track
            where p.id = :id
            """)
    Optional<Playlist> findByIdWithItemsForMaintenance(@Param("id") UUID id);

    /** Playlists holding a given track, so a library-wide delete can close their gaps. */
    @Query("select distinct i.playlist.id from PlaylistItem i where i.track.id = :trackId")
    List<UUID> findIdsContainingTrack(@Param("trackId") UUID trackId);
}
