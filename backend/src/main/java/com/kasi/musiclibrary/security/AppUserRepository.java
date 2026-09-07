package com.kasi.musiclibrary.security;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /**
     * Case-insensitive, matching the unique index in V3, which is on lower(username).
     * Signing in as "Admin" and as "admin" must reach the same row.
     */
    Optional<AppUser> findByUsernameIgnoreCase(String username);
}
