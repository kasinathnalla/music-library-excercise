package com.kasi.musiclibrary.service;

import com.kasi.musiclibrary.entity.FieldEdit;
import com.kasi.musiclibrary.repository.FieldEditRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Tracks which fields a person has set by hand, so automated sources never overwrite them.
 *
 * <p>Nothing enriches metadata automatically yet, so today this records and reports. The
 * record has to start being kept now rather than when enrichment arrives, because by then
 * the information about which values were hand-corrected is gone.
 */
@Service
public class ProvenanceService {

    public static final String TRACK = "TRACK";

    private final FieldEditRepository fieldEdits;

    public ProvenanceService(FieldEditRepository fieldEdits) {
        this.fieldEdits = fieldEdits;
    }

    @Transactional
    public void recordUserEdit(String entityType, UUID entityId, String fieldName) {
        if (fieldEdits.existsByEntityTypeAndEntityIdAndFieldName(entityType, entityId, fieldName)) {
            return;
        }
        fieldEdits.save(new FieldEdit(entityType, entityId, fieldName));
    }

    public List<String> userEditedFields(String entityType, UUID entityId) {
        return fieldEdits.findFieldNames(entityType, entityId);
    }

    /** True when an automated source must leave this field alone. */
    public boolean isUserEdited(String entityType, UUID entityId, String fieldName) {
        return fieldEdits.existsByEntityTypeAndEntityIdAndFieldName(entityType, entityId, fieldName);
    }

    @Transactional
    public void forget(String entityType, UUID entityId) {
        fieldEdits.deleteByEntityTypeAndEntityId(entityType, entityId);
    }
}
