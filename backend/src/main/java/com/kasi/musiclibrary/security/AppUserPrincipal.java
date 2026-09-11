package com.kasi.musiclibrary.security;

import com.kasi.musiclibrary.entity.AppUser;
import com.kasi.musiclibrary.entity.Role;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The authenticated user, carrying its database id.
 *
 * <p>Spring's own {@code User} would do for authentication, but the id is needed when
 * recording who uploaded a track, and carrying it here avoids a second lookup by username on
 * every upload. The password hash is deliberately not retained beyond authentication.
 */
public record AppUserPrincipal(UUID id, String username, String passwordHash, Role role,
                               boolean enabled, String firstName, String lastName)
        implements UserDetails {

    public static AppUserPrincipal of(AppUser user) {
        return new AppUserPrincipal(user.getId(), user.getUsername(), user.getPasswordHash(),
                user.getRole(), user.isEnabled(), user.getFirstName(), user.getLastName());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // The ROLE_ prefix is what hasRole("ADMIN") looks for. Omit it and a genuine admin
        // gets a 403, which is the most common way this configuration goes wrong.
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    // Spelled out rather than relying on interface defaults, which have moved between
    // Spring Security versions. There is no expiry or lockout in this version.
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
