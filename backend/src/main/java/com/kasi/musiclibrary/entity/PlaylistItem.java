package com.kasi.musiclibrary.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.util.UUID;

/**
 * One track at one position in one playlist.
 *
 * <p>It has its own id rather than being keyed by {@code (playlist_id, track_id)}, because the
 * same track may appear twice in a playlist and "remove the second copy" has to be expressible.
 */
@Entity
public class PlaylistItem {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "playlist_id", nullable = false)
    private Playlist playlist;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id", nullable = false)
    private Track track;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant addedAt;

    protected PlaylistItem() {
    }

    PlaylistItem(Playlist playlist, Track track, int position) {
        this.playlist = playlist;
        this.track = track;
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public Playlist getPlaylist() {
        return playlist;
    }

    public Track getTrack() {
        return track;
    }

    public int getPosition() {
        return position;
    }

    void setPosition(int position) {
        this.position = position;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
