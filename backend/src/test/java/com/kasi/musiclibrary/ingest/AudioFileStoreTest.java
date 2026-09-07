package com.kasi.musiclibrary.ingest;

import com.kasi.musiclibrary.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain unit tests: this class touches the filesystem but not the database, so it needs no
 * Spring context and runs in milliseconds.
 */
class AudioFileStoreTest {

	@TempDir
	Path mediaRoot;

	private AudioFileStore store() {
		return new AudioFileStore(new StorageProperties(mediaRoot.toString()));
	}

	@Test
	void writesBytesAndReportsSizeAndHash() throws Exception {
		byte[] content = "pretend this is an mp3".getBytes(StandardCharsets.UTF_8);

		StoredAudio stored = store().store(new ByteArrayInputStream(content), "song.mp3");

		assertThat(stored.size()).isEqualTo(content.length);
		assertThat(stored.contentHash()).hasSize(64).matches("[0-9a-f]{64}");
		assertThat(Files.readAllBytes(mediaRoot.resolve(stored.relativePath()))).isEqualTo(content);
	}

	@Test
	void shardsPathsByHashSoOneDirectoryNeverHoldsEveryTrack() {
		StoredAudio stored = store().store(
				new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)), "song.mp3");

		// sha256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
		assertThat(stored.relativePath()).isEqualTo(
				"ba/78/ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad.mp3");
	}

	@Test
	void identicalBytesProduceTheSameHashRegardlessOfFilename() {
		AudioFileStore store = store();

		StoredAudio first = store.store(
				new ByteArrayInputStream("same".getBytes(StandardCharsets.UTF_8)), "one.mp3");
		StoredAudio second = store.store(
				new ByteArrayInputStream("same".getBytes(StandardCharsets.UTF_8)), "two.mp3");

		// This is what makes duplicate detection work on content rather than on name.
		assertThat(second.contentHash()).isEqualTo(first.contentHash());
	}

	@Test
	void resolvesAStoredPathBackToAnAbsolutePathForReading() {
		AudioFileStore store = store();
		StoredAudio stored = store.store(
				new ByteArrayInputStream("xyz".getBytes(StandardCharsets.UTF_8)), "song.flac");

		Path resolved = store.resolve(stored.relativePath());

		assertThat(resolved).exists().isAbsolute();
		assertThat(resolved.toString()).endsWith(".flac");
	}

	/**
	 * StreamController serves whatever resolve() returns, so a path escaping the media root
	 * would be an arbitrary file read. The guard belongs here, at the boundary, rather than in
	 * each caller.
	 */
	@Test
	void refusesAPathThatEscapesTheMediaRoot() {
		AudioFileStore store = store();

		assertThatThrownBy(() -> store.resolve("../../etc/passwd"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("escapes");

		assertThatThrownBy(() -> store.resolve("ab/../../../../etc/passwd"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void refusesAnAbsolutePathOutsideTheMediaRoot() {
		assertThatThrownBy(() -> store().resolve("/etc/passwd"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void ignoresAnImplausibleFileExtension() {
		StoredAudio stored = store().store(
				new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)),
				"song.thisisnotanextension");

		assertThat(stored.relativePath()).doesNotContain("thisisnotanextension");
	}

	@Test
	void leavesNoPartialFileBehindAfterAWrite() throws Exception {
		store().store(new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), "a.mp3");

		try (var entries = Files.list(mediaRoot)) {
			assertThat(entries.filter(p -> p.getFileName().toString().endsWith(".part")))
					.as("staging files must be moved into place, not left in the root")
					.isEmpty();
		}
	}
}
