package com.kasi.musiclibrary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = """
        A partial update. Any field left out is unchanged.

        For albumTitle and artistName an empty string means "remove this association" rather
        than "set it to blank", which is how a track ends up with no album.
        """)
public record TrackUpdateRequest(

        @Schema(description = "New track title. Must not be blank.", example = "Prelude in C")
        String title,

        @Schema(description = """
                Moves the track to this album, matching an existing album by title and credited
                artist before creating one. Empty string removes the album.
                """)
        String albumTitle,

        @Schema(description = """
                Moves the track's album under this artist, matching an existing artist by name
                before creating one. Empty string removes the artist.
                """)
        String artistName,

        @Min(0) @Max(999)
        @Schema(description = "Position within the disc", example = "3")
        Integer trackNumber,

        @Min(0) @Max(99)
        @Schema(description = "Disc number for multi-disc releases", example = "1")
        Integer discNumber,

        @Min(0) @Max(9999)
        @Schema(description = "Release year of the album", example = "1998")
        Integer releaseYear) {
}
