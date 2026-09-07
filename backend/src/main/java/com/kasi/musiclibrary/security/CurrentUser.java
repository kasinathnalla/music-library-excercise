package com.kasi.musiclibrary.security;

/**
 * Who the caller is, as the API reports it.
 *
 * <p>An explicit record rather than the entity, per the convention in AGENTS.md -- and here
 * that is not only style: {@link AppUser} carries the password hash, and serializing it would
 * put every hash on the wire.
 */
public record CurrentUser(String username, String role, String firstName, String lastName) {

    /** For the two seeded accounts, which predate profile fields, and for a caller that has none. */
    public static CurrentUser withoutName(String username, String role) {
        return new CurrentUser(username, role, null, null);
    }
}
