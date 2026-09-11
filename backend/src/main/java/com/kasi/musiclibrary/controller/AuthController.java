package com.kasi.musiclibrary.controller;

import com.kasi.musiclibrary.dto.CurrentUser;
import com.kasi.musiclibrary.dto.ErrorResponse;
import com.kasi.musiclibrary.dto.RegisterRequest;
import com.kasi.musiclibrary.entity.AppUser;
import com.kasi.musiclibrary.entity.Role;
import com.kasi.musiclibrary.repository.AppUserRepository;
import com.kasi.musiclibrary.security.AppUserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Signing in, and knowing who you are")
public class AuthController {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Operation(
            summary = "Who am I",
            description = """
                    Returns the signed-in user and their role, or 401 if there is no session.

                    This endpoint is the whole sign-in protocol. Call it with HTTP Basic
                    credentials to sign in: a successful response also establishes a session,
                    and every later request -- including the audio the browser fetches for
                    itself -- rides that session. Call it with no credentials on page load to
                    discover whether an existing session is still valid.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Signed in"),
            @ApiResponse(responseCode = "401", description = "No credentials, or bad ones")
    })
    @GetMapping("/me")
    public CurrentUser me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in");
        }
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .findFirst()
                .orElse("CUSTOMER");

        // The seeded accounts, and any principal built by @WithMockUser in tests, carry no
        // name. Falling back to the bare username is a real case, not defensive noise.
        if (authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return new CurrentUser(authentication.getName(), role,
                    principal.firstName(), principal.lastName());
        }
        return CurrentUser.withoutName(authentication.getName(), role);
    }

    @Operation(
            summary = "Create a customer account",
            description = """
                    Self-registration, open to anyone. Every account created this way is a
                    CUSTOMER: there is no field on this request that a caller could set to make
                    an admin, by design, not by a check that could be forgotten. Provisioning an
                    admin account is done directly against the database, the same way the
                    seeded accounts are.

                    The username is unique, case-insensitively, matching the sign-in check.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "400", description = "A username or password did not meet the rules"),
            @ApiResponse(responseCode = "409", description = "That username is already taken")
    })
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            AppUser created = users.save(new AppUser(request.username(),
                    passwordEncoder.encode(request.password()), Role.CUSTOMER,
                    request.firstName(), request.lastName(), request.dateOfBirth(),
                    request.address()));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new CurrentUser(created.getUsername(), created.getRole().name(),
                            created.getFirstName(), created.getLastName()));
        } catch (DataIntegrityViolationException e) {
            // The unique index on lower(username) is the actual guard; this only turns its
            // violation into a message a client can show, in the same ErrorResponse shape
            // every other handled conflict in this API uses (see DuplicateTrackException).
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("That username is already taken"));
        }
    }

    @Operation(summary = "Sign out", description = "Invalidates the session. Always 204.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
