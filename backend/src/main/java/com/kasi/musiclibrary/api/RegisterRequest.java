package com.kasi.musiclibrary.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * What a self-registering visitor supplies. Deliberately has no {@code role} field: there is
 * nothing in this shape for a caller to set to ADMIN, so the endpoint cannot be tricked into
 * creating one by sending extra JSON. See AuthController.register.
 */
public record RegisterRequest(
        @NotBlank(message = "Choose a username")
        @Size(min = 3, max = 64, message = "Usernames are 3 to 64 characters")
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "Usernames may only contain letters, numbers, dots, underscores, and hyphens")
        String username,

        @NotBlank(message = "Choose a password")
        @Size(min = 6, max = 200, message = "Passwords need at least 6 characters")
        String password) {
}
