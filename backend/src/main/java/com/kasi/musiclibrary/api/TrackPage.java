package com.kasi.musiclibrary.api;

import org.springframework.data.domain.Page;
import java.util.List;

public record TrackPage(
        List<TrackResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static TrackPage from(Page<com.kasi.musiclibrary.catalog.Track> source) {
        return new TrackPage(
                source.getContent().stream().map(TrackResponse::from).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages());
    }
}
