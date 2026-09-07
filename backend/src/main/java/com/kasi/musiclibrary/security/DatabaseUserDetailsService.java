package com.kasi.musiclibrary.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public DatabaseUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return users.findByUsernameIgnoreCase(username)
                .map(AppUserPrincipal::of)
                // An unknown username and a wrong password must be indistinguishable to the
                // caller: both end as 401 with no body. Anything else is a user enumeration
                // oracle.
                .orElseThrow(() -> new UsernameNotFoundException("Bad credentials"));
    }
}
