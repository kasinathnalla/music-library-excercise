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
public class Track {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id")
    private Album album;

    private Short trackNumber;
    private Short discNumber;
    private Integer durationMs;

    @Column(nullable = false)
    private String filePath;

    @Column(nullable = false)
    private long fileSize;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant addedAt;

    /**
     * Who uploaded this, or null for the tracks seeded at boot, when no user is authenticated.
     *
     * <p>A raw id rather than a ManyToOne on purpose: open-in-view is off, and an association
     * here would be one more lazy proxy that a response could touch after the session closed.
     * Nothing reads this column yet.
     */
    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    protected Track() {
    }

    public Track(String title, Album album, Integer trackNumber, Integer discNumber,
                 Integer durationMs, String filePath, long fileSize, String contentType,
                 String contentHash) {
        this.title = title;
        this.album = album;
        this.trackNumber = trackNumber == null ? null : trackNumber.shortValue();
        this.discNumber = discNumber == null ? null : discNumber.shortValue();
        this.durationMs = durationMs;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.contentHash = contentHash;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Album getAlbum() {
        return album;
    }

    public void setAlbum(Album album) {
        this.album = album;
    }

    public Integer getTrackNumber() {
        return trackNumber == null ? null : trackNumber.intValue();
    }

    public void setTrackNumber(Integer trackNumber) {
        this.trackNumber = trackNumber == null ? null : trackNumber.shortValue();
    }

    public Integer getDiscNumber() {
        return discNumber == null ? null : discNumber.intValue();
    }

    public void setDiscNumber(Integer discNumber) {
        this.discNumber = discNumber == null ? null : discNumber.shortValue();
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public String getFilePath() {
        return filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getContentType() {
        return contentType;
    }

    public String getContentHash() {
        return contentHash;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(UUID uploadedBy) {
        this.uploadedBy = uploadedBy;
    }
}
