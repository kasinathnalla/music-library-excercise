package com.kasi.musiclibrary.repository;

import com.kasi.musiclibrary.entity.Track;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface TrackRepository extends JpaRepository<Track, UUID> {

    Optional<Track> findByContentHash(String contentHash);

    /**
     * Loads a track with its album and artist already fetched.
     *
     * <p>Needed because open-in-view is off. A plain findById returns a track whose
     * album is a lazy proxy, and the session is closed by the time the response is
     * serialized, so touching the album throws LazyInitializationException.
     */
    @Query("""
            select t from Track t
            left join fetch t.album a
            left join fetch a.albumArtist
            where t.id = :id
            """)
    Optional<Track> findByIdWithAlbum(@Param("id") UUID id);

    long countByAlbumId(UUID albumId);

    /** Coalesced so an empty library reports zero rather than null. */
    @Query("select coalesce(sum(t.durationMs), 0) from Track t")
    long totalDurationMs();

    @Query(value = """
            select t from Track t
            left join fetch t.album a
            left join fetch a.albumArtist ar
            where :query is null
               or lower(t.title) like lower(concat('%', cast(:query as string), '%'))
               or lower(a.title) like lower(concat('%', cast(:query as string), '%'))
               or lower(ar.name) like lower(concat('%', cast(:query as string), '%'))
            """,
            countQuery = """
            select count(t) from Track t
            left join t.album a
            left join a.albumArtist ar
            where :query is null
               or lower(t.title) like lower(concat('%', cast(:query as string), '%'))
               or lower(a.title) like lower(concat('%', cast(:query as string), '%'))
               or lower(ar.name) like lower(concat('%', cast(:query as string), '%'))
            """)
    Page<Track> search(@Param("query") String query, Pageable pageable);
}
