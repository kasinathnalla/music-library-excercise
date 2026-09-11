package com.kasi.musiclibrary.dto;

import com.kasi.musiclibrary.entity.Playlist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePlaylistRequest(
        @NotBlank(message = "Give the playlist a name")
        @Size(max = 200, message = "Playlist names are at most 200 characters")
        String name) {
}
