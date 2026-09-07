package com.kasi.musiclibrary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "musiclibrary.storage")
public record StorageProperties(String mediaRoot) {
}
