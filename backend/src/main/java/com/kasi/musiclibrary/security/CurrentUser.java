package com.kasi.musiclibrary.security;

/**
 * Who the caller is, as the API reports it.
 *
 * <p>An explicit record rather than the entity, per the convention in AGENTS.md -- and here
 * that is not only style: {@link AppUser} carries the password hash, and serializing it would
 * put every hash on the wire.
 */
public record CurrentUser(String username, String role) {
}
