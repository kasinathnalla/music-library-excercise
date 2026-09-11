package com.kasi.musiclibrary.service;

import com.kasi.musiclibrary.dto.ParsedTags;
import com.kasi.musiclibrary.exception.UnreadableAudioException;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class AudioTagReader {

    static {
        Logger.getLogger("org.jaudiotagger").setLevel(Level.SEVERE);
    }

    public ParsedTags read(Path file) {
        AudioFile audioFile;
        try {
            audioFile = AudioFileIO.read(file.toFile());
        } catch (Exception e) {
            throw new UnreadableAudioException(
                    "Could not read audio from " + file.getFileName(), e);
        }

        Tag tag = audioFile.getTag();
        Integer durationMs = audioFile.getAudioHeader() == null
                ? null
                : audioFile.getAudioHeader().getTrackLength() * 1000;

        if (tag == null) {
            return new ParsedTags(null, null, null, null, null, null, null, durationMs);
        }

        return new ParsedTags(
                text(tag, FieldKey.TITLE),
                text(tag, FieldKey.ARTIST),
                text(tag, FieldKey.ALBUM),
                text(tag, FieldKey.ALBUM_ARTIST),
                number(text(tag, FieldKey.YEAR)),
                number(text(tag, FieldKey.TRACK)),
                number(text(tag, FieldKey.DISC_NO)),
                durationMs);
    }

    private String text(Tag tag, FieldKey key) {
        try {
            String value = tag.getFirst(key);
            return value == null || value.isBlank() ? null : value.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private Integer number(String value) {
        if (value == null) {
            return null;
        }
        String head = value.split("[/-]")[0].trim();
        try {
            return Integer.valueOf(head);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
