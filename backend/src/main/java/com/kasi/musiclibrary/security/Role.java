package com.kasi.musiclibrary.security;

/**
 * What a user is allowed to do. Two roles, and no user holds both.
 *
 * <p>ADMIN curates the library: upload, edit, delete. CUSTOMER browses, searches, and listens.
 * The mapping from role to endpoints is not here -- it is in {@link SecurityConfig}, so the
 * whole authorization matrix can be read in one screen.
 */
public enum Role {
    ADMIN,
    CUSTOMER
}
