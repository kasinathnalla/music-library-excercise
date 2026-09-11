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

@Entity
public class Album {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_artist_id")
    private Artist albumArtist;

    private Short releaseYear;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Album() {
    }

    public Album(String title, Artist albumArtist, Integer releaseYear) {
        this.title = title;
        this.albumArtist = albumArtist;
        this.releaseYear = releaseYear == null ? null : releaseYear.shortValue();
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Artist getAlbumArtist() {
        return albumArtist;
    }

    public Integer getReleaseYear() {
        return releaseYear == null ? null : releaseYear.intValue();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
