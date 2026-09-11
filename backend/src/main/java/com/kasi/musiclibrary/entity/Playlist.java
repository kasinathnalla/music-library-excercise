package com.kasi.musiclibrary.entity;

import com.kasi.musiclibrary.exception.InvalidReorderException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A listener's own ordered arrangement of the shared library.
 *
 * <p>The owner is a raw id rather than a {@code @ManyToOne AppUser}, following the same reasoning
 * recorded on {@link Track#getUploadedBy()}: open-in-view is off, so
 * an association here would be one more lazy proxy a response could touch after the session
 * closed. It also means no code path exists by which serializing a playlist could reach an
 * {@code AppUser} and put its password hash on the wire.
 */
@Entity
public class Playlist {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * Ordered by {@code position}, which is the stored order and not the insertion order.
     *
     * <p>{@code orphanRemoval} is what makes {@link #removeItem} a delete rather than an orphan
     * row with a dangling playlist_id.
     */
    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<PlaylistItem> items = new ArrayList<>();

    protected Playlist() {
    }

    public Playlist(UUID ownerId, String name) {
        this.ownerId = ownerId;
        this.name = name;
        this.updatedAt = Instant.now();
    }

    /** Appends to the end. A track may legitimately appear more than once in one playlist. */
    public PlaylistItem add(Track track) {
        PlaylistItem item = new PlaylistItem(this, track, items.size());
        items.add(item);
        touch();
        return item;
    }

    /**
     * Removes one item and closes the gap, so positions stay contiguous from zero.
     *
     * <p>Renumbering here rather than leaving holes keeps "position" meaning "index in the list",
     * which is what both the reorder contract and the play queue assume.
     *
     * @return false when no item with that id belongs to this playlist
     */
    public boolean removeItem(UUID itemId) {
        boolean removed = items.removeIf(item -> itemId.equals(item.getId()));
        if (removed) {
            renumber();
            touch();
        }
        return removed;
    }

    /**
     * Rewrites every position to match the given order.
     *
     * <p>The caller sends the whole order (D23), so this validates that the submitted ids are
     * exactly the current item set -- no additions, no omissions, no duplicates. A reorder that
     * quietly dropped an item would be data loss wearing the costume of a sort.
     *
     * @throws InvalidReorderException when the submitted ids are not a permutation of the current ones
     */
    public void reorder(List<UUID> itemIdsInOrder) {
        Set<UUID> submitted = new HashSet<>(itemIdsInOrder);
        Set<UUID> current = new HashSet<>(items.stream().map(PlaylistItem::getId).toList());
        if (submitted.size() != itemIdsInOrder.size() || !submitted.equals(current)) {
            throw new InvalidReorderException(
                    "The submitted order must list every item in this playlist exactly once");
        }
        // Positions collide with themselves part way through any real reorder. That is safe only
        // because playlist_item_position_key is deferred to commit time -- see V6__playlists.sql.
        for (int position = 0; position < itemIdsInOrder.size(); position++) {
            UUID itemId = itemIdsInOrder.get(position);
            for (PlaylistItem item : items) {
                if (itemId.equals(item.getId())) {
                    item.setPosition(position);
                    break;
                }
            }
        }
        items.sort((a, b) -> Integer.compare(a.getPosition(), b.getPosition()));
        touch();
    }

    /**
     * Drops every copy of a track, for when it is deleted from the library entirely.
     *
     * <p>Done through JPA rather than left to the foreign key cascade in V6. The cascade would
     * remove the rows correctly but Hibernate would not know it had happened, leaving stale
     * PlaylistItem entities in the persistence context and positions that never get closed up.
     *
     * @return true when this playlist held the track
     */
    public boolean removeItemsForTrack(UUID trackId) {
        boolean removed = items.removeIf(item -> trackId.equals(item.getTrack().getId()));
        if (removed) {
            renumber();
            touch();
        }
        return removed;
    }

    /** Closes gaps left by a removal, including one done by a database cascade. */
    public void renumber() {
        items.sort((a, b) -> Integer.compare(a.getPosition(), b.getPosition()));
        for (int position = 0; position < items.size(); position++) {
            items.get(position).setPosition(position);
        }
    }

    public void rename(String newName) {
        this.name = newName;
        touch();
    }

    /** What the playlist list is ordered by, so every mutation has to call it. */
    public void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<PlaylistItem> getItems() {
        return items;
    }
}
