package com.kasi.musiclibrary.ingest;

import java.util.UUID;

public record IngestResult(UUID trackId, String title) {
}
