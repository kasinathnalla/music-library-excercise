package com.kasi.musiclibrary.api;

import com.kasi.musiclibrary.repository.AlbumRepository;
import com.kasi.musiclibrary.repository.ArtistRepository;
import com.kasi.musiclibrary.repository.TrackRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs as an admin. These are catalog tests, not authorization tests: they assert what the
 * endpoints do, and {@link com.kasi.musiclibrary.security.AuthorizationMatrixTest} asserts who
 * may reach them. A mock user is used rather than the seeded accounts so that this file does
 * not also depend on what V3 inserted.
 */
@WithMockUser(roles = "ADMIN")
class TrackControllerTest extends SecuredMockMvcTest {

	@Autowired
	private TrackRepository tracks;
	@Autowired
	private AlbumRepository albums;
	@Autowired
	private ArtistRepository artists;

	private byte[] tagged;

	@BeforeEach
	void setUp() throws Exception {
		tracks.deleteAll();
		albums.deleteAll();
		artists.deleteAll();
		tagged = Files.readAllBytes(Path.of("src/test/resources/fixtures/tagged.mp3"));
	}

	private MockMultipartFile upload(byte[] bytes, String filename) {
		return new MockMultipartFile("file", filename, "audio/mpeg", bytes);
	}

	/**
	 * Regression guard. With open-in-view off, building the response from a plain
	 * findById leaves album as a lazy proxy on a closed session, and reading
	 * artistName throws LazyInitializationException, which surfaces as a 500.
	 */
	@Test
	void uploadReturnsCreatedWithTheAlbumAndArtistResolved() throws Exception {
		mockMvc.perform(multipart("/api/tracks").file(upload(tagged, "tone.mp3")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("Test Tone A"))
				.andExpect(jsonPath("$.albumTitle").value("Sine Qua Non"))
				.andExpect(jsonPath("$.artistName").value("The Oscillators"))
				.andExpect(jsonPath("$.durationMs").isNumber())
				.andExpect(jsonPath("$.id").isNotEmpty());
	}

	@Test
	void fetchingASingleTrackAlsoResolvesTheAlbumAndArtist() throws Exception {
		String body = mockMvc.perform(multipart("/api/tracks").file(upload(tagged, "tone.mp3")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String id = body.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

		mockMvc.perform(get("/api/tracks/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.artistName").value("The Oscillators"));
	}

	@Test
	void uploadingTheSameBytesTwiceIsAConflictNotAServerError() throws Exception {
		mockMvc.perform(multipart("/api/tracks").file(upload(tagged, "tone.mp3")))
				.andExpect(status().isCreated());

		mockMvc.perform(multipart("/api/tracks").file(upload(tagged, "again.mp3")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("This audio is already in the library"));
	}

	@Test
	void uploadingSomethingThatIsNotAudioIsABadRequest() throws Exception {
		mockMvc.perform(multipart("/api/tracks")
						.file(upload("plain text".getBytes(), "notes.mp3")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").isNotEmpty());
	}

	@Test
	void listReturnsAPageWithTotalCount() throws Exception {
		mockMvc.perform(multipart("/api/tracks").file(upload(tagged, "tone.mp3")));

		mockMvc.perform(get("/api/tracks"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.items[0].title").value("Test Tone A"));
	}

	@Test
	void listFiltersByQueryAcrossTitleAlbumAndArtist() throws Exception {
		mockMvc.perform(multipart("/api/tracks").file(upload(tagged, "tone.mp3")));

		mockMvc.perform(get("/api/tracks").param("query", "oscillat"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1));

		mockMvc.perform(get("/api/tracks").param("query", "nothing matches this"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	/** Regression guard for the left-join decision: an album-less track must still appear. */
	@Test
	void listIncludesTracksThatHaveNoAlbum() throws Exception {
		byte[] untagged = Files.readAllBytes(Path.of("src/test/resources/fixtures/untagged.mp3"));
		mockMvc.perform(multipart("/api/tracks").file(upload(untagged, "Solo Piece.mp3")))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/tracks").param("query", "Solo"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items[0].albumTitle").doesNotExist());
	}
}
