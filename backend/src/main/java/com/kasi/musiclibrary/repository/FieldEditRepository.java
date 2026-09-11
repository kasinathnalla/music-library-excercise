package com.kasi.musiclibrary.repository;

import com.kasi.musiclibrary.entity.FieldEdit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FieldEditRepository extends JpaRepository<FieldEdit, UUID> {

    @Query("select f.fieldName from FieldEdit f where f.entityType = :type and f.entityId = :id")
    List<String> findFieldNames(@Param("type") String entityType, @Param("id") UUID entityId);

    boolean existsByEntityTypeAndEntityIdAndFieldName(String entityType, UUID entityId, String fieldName);

    void deleteByEntityTypeAndEntityId(String entityType, UUID entityId);
}
