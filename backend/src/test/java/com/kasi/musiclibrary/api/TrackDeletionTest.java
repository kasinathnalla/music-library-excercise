package com.kasi.musiclibrary.api;

import com.kasi.musiclibrary.catalog.AlbumRepository;
import com.kasi.musiclibrary.catalog.ArtistRepository;
import com.kasi.musiclibrary.catalog.TrackRepository;
import com.kasi.musiclibrary.support.SecuredMockMvcTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs as an admin. These are catalog tests, not authorization tests: they assert what the
 * endpoints do, and {@link com.kasi.musiclibrary.security.AuthorizationMatrixTest} asserts who
 * may reach them. A mock user is used rather than the seeded accounts so that this file does
 * not also depend on what V3 inserted.
 */
@WithMockUser(roles = "ADMIN")
class TrackDeletionTest extends SecuredMockMvcTest {

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

	private String uploadAndGetId(byte[] bytes, String filename) throws Exception {
		String body = mockMvc.perform(multipart("/api/tracks")
						.file(new MockMultipartFile("file", filename, "audio/mpeg", bytes)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return body.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
	}

	@Test
	void deletingATrackRemovesItFromTheLibrary() throws Exception {
		String id = uploadAndGetId(tagged, "tone.mp3");

		mockMvc.perform(delete("/api/tracks/{id}", id)).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/tracks/{id}", id)).andExpect(status().isNotFound());
		assertThat(tracks.count()).isZero();
	}

	@Test
	void deletingTheLastTrackOfAnAlbumRemovesTheAlbumAndItsArtist() throws Exception {
		String id = uploadAndGetId(tagged, "tone.mp3");
		assertThat(albums.count()).isEqualTo(1);
		assertThat(artists.count()).isEqualTo(1);

		mockMvc.perform(delete("/api/tracks/{id}", id)).andExpect(status().isNoContent());

		assertThat(albums.count()).isZero();
		assertThat(artists.count()).isZero();
	}

	@Test
	void deletingOneOfTwoTracksLeavesTheAlbumIntact() throws Exception {
		uploadAndGetId(tagged, "tone.mp3");

		byte[] second = tagged.clone();
		second[second.length - 1] = (byte) (second[second.length - 1] ^ 0x01);
		String secondId = uploadAndGetId(second, "tone2.mp3");

		assertThat(tracks.count()).isEqualTo(2);
		assertThat(albums.count()).isEqualTo(1);

		mockMvc.perform(delete("/api/tracks/{id}", secondId)).andExpect(status().isNoContent());

		assertThat(tracks.count()).isEqualTo(1);
		assertThat(albums.count()).isEqualTo(1);
		assertThat(artists.count()).isEqualTo(1);
	}

	@Test
	void deletingRemovesTheStoredAudioSoTheSameFileCanBeUploadedAgain() throws Exception {
		String id = uploadAndGetId(tagged, "tone.mp3");
		mockMvc.perform(delete("/api/tracks/{id}", id)).andExpect(status().isNoContent());

		// Would be a 409 if the row or its bytes had survived.
		mockMvc.perform(multipart("/api/tracks")
						.file(new MockMultipartFile("file", "tone.mp3", "audio/mpeg", tagged)))
				.andExpect(status().isCreated());
	}

	@Test
	void deletingSomethingThatIsNotThereIsANotFound() throws Exception {
		mockMvc.perform(delete("/api/tracks/{id}", UUID.randomUUID()))
				.andExpect(status().isNotFound());
	}
}
