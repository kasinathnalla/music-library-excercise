package com.kasi.musiclibrary.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AlbumRepository extends JpaRepository<Album, UUID> {
    Optional<Album> findByTitleIgnoreCaseAndAlbumArtist(String title, Artist albumArtist);

    long countByAlbumArtistId(UUID artistId);
}
