package com.kasi.musiclibrary.dto;

import java.util.UUID;

public record IngestResult(UUID trackId, String title) {
}
