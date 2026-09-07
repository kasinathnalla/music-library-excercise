package com.kasi.musiclibrary.provenance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.UUID;

/** One field, on one entity, that a person set by hand. */
@Entity
public class FieldEdit {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String entityType;

    @Column(nullable = false)
    private UUID entityId;

    @Column(nullable = false)
    private String fieldName;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant editedAt;

    protected FieldEdit() {
    }

    public FieldEdit(String entityType, UUID entityId, String fieldName) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.fieldName = fieldName;
    }

    public UUID getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Instant getEditedAt() {
        return editedAt;
    }
}
