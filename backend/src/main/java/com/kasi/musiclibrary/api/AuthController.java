package com.kasi.musiclibrary.api;

import com.kasi.musiclibrary.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Signing in, and knowing who you are")
public class AuthController {

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
        return new CurrentUser(authentication.getName(), role);
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
