package com.kasi.musiclibrary.repository;

import com.kasi.musiclibrary.entity.Album;
import com.kasi.musiclibrary.entity.Artist;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AlbumRepository extends JpaRepository<Album, UUID> {
    Optional<Album> findByTitleIgnoreCaseAndAlbumArtist(String title, Artist albumArtist);

    long countByAlbumArtistId(UUID artistId);
}
