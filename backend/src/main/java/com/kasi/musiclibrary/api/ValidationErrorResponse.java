package com.kasi.musiclibrary.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * The body returned for a failed {@code @Valid} check on a request body.
 *
 * <p>A sibling of {@link ErrorResponse} rather than a variant of it: this shape always carries
 * a field-by-field breakdown, which a duplicate-upload or a not-found response never has reason
 * to. A client that only reads {@code message} still gets something readable; one that wants to
 * mark the specific offending fields in a form can use {@code fieldErrors} instead.
 */
@Schema(description = "Error body for a request that failed field validation")
public record ValidationErrorResponse(
        @Schema(description = "Every failing field's message, joined for display",
                example = "Address is required; Date of birth must be in the past")
        String message,

        @Schema(description = "Field name to its own validation message")
        Map<String, String> fieldErrors) {
}
