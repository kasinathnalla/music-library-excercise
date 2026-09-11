package com.kasi.musiclibrary.repository;

import com.kasi.musiclibrary.entity.Artist;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ArtistRepository extends JpaRepository<Artist, UUID> {
    Optional<Artist> findByNameIgnoreCase(String name);
}
