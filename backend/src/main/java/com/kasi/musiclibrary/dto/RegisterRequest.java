package com.kasi.musiclibrary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * What a self-registering visitor supplies. Deliberately has no {@code role} field: there is
 * nothing in this shape for a caller to set to ADMIN, so the endpoint cannot be tricked into
 * creating one by sending extra JSON. See AuthController.register.
 */
public record RegisterRequest(
        @NotBlank(message = "Choose a username")
        @Size(min = 3, max = 254, message = "Usernames are 3 to 254 characters")
        @Pattern(regexp = "^[A-Za-z0-9_.+@-]+$",
                message = "Usernames may only contain letters, numbers, and . _ + @ -")
        String username,

        @NotBlank(message = "Choose a password")
        @Size(min = 6, max = 200, message = "Passwords need at least 6 characters")
        String password,

        @NotBlank(message = "First name is required")
        @Size(max = 100)
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100)
        String lastName,

        @NotNull(message = "Date of birth is required")
        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        @NotBlank(message = "Address is required")
        @Size(max = 500)
        String address) {
}
