package com.kasi.musiclibrary.api;

import com.kasi.musiclibrary.catalog.AlbumRepository;
import com.kasi.musiclibrary.catalog.ArtistRepository;
import com.kasi.musiclibrary.catalog.TrackRepository;
import com.kasi.musiclibrary.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TrackEditTest extends PostgresIntegrationTest {

	@Autowired
	private WebApplicationContext context;
	@Autowired
	private TrackRepository tracks;
	@Autowired
	private AlbumRepository albums;
	@Autowired
	private ArtistRepository artists;

	private MockMvc mockMvc;
	private byte[] tagged;

	@BeforeEach
	void setUp() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		tracks.deleteAll();
		albums.deleteAll();
		artists.deleteAll();
		tagged = Files.readAllBytes(Path.of("src/test/resources/fixtures/tagged.mp3"));
	}

	/** The fixture is tagged: "Test Tone A" / "The Oscillators" / "Sine Qua Non" / 1998. */
	private String upload(byte[] bytes, String filename) throws Exception {
		String body = mockMvc.perform(multipart("/api/tracks")
						.file(new MockMultipartFile("file", filename, "audio/mpeg", bytes)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return body.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
	}

	private byte[] variantOf(byte[] source, int seed) {
		byte[] copy = source.clone();
		copy[copy.length - 1] = (byte) (copy[copy.length - 1] ^ seed);
		return copy;
	}

	private org.springframework.test.web.servlet.ResultActions patchTrack(String id, String json)
			throws Exception {
		return mockMvc.perform(patch("/api/tracks/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

	@Test
	void editsTheTitle() throws Exception {
		String id = upload(tagged, "tone.mp3");

		patchTrack(id, """
				{"title": "Test Tone A (remastered)"}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Test Tone A (remastered)"))
				.andExpect(jsonPath("$.albumTitle").value("Sine Qua Non"));
	}

	@Test
	void omittedFieldsAreLeftAlone() throws Exception {
		String id = upload(tagged, "tone.mp3");

		patchTrack(id, """
				{"title": "Only the title changes"}
				""").andExpect(status().isOk());

		mockMvc.perform(get("/api/tracks/{id}", id))
				.andExpect(jsonPath("$.artistName").value("The Oscillators"))
				.andExpect(jsonPath("$.albumTitle").value("Sine Qua Non"))
				.andExpect(jsonPath("$.trackNumber").value(3));
	}

	@Test
	void changingTheAlbumMovesTheTrackAndCleansUpTheAlbumItLeft() throws Exception {
		String id = upload(tagged, "tone.mp3");
		assertThat(albums.count()).isEqualTo(1);

		patchTrack(id, """
				{"albumTitle": "A Different Record"}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.albumTitle").value("A Different Record"))
				.andExpect(jsonPath("$.artistName").value("The Oscillators"));

		// The old album had only this track, so it should be gone rather than left empty.
		assertThat(albums.count()).isEqualTo(1);
		assertThat(albums.findAll().getFirst().getTitle()).isEqualTo("A Different Record");
	}

	@Test
	void movingOneOfTwoTracksLeavesTheOriginalAlbumInPlace() throws Exception {
		upload(tagged, "one.mp3");
		String second = upload(variantOf(tagged, 0x01), "two.mp3");
		assertThat(albums.count()).isEqualTo(1);

		patchTrack(second, """
				{"albumTitle": "Somewhere Else"}
				""").andExpect(status().isOk());

		assertThat(albums.count()).isEqualTo(2);
	}

	@Test
	void changingTheArtistReattachesTheAlbumUnderTheNewArtist() throws Exception {
		String id = upload(tagged, "tone.mp3");

		patchTrack(id, """
				{"artistName": "Somebody Else"}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.artistName").value("Somebody Else"))
				.andExpect(jsonPath("$.albumTitle").value("Sine Qua Non"));

		assertThat(artists.count()).isEqualTo(1);
		assertThat(artists.findAll().getFirst().getName()).isEqualTo("Somebody Else");
	}

	@Test
	void twoTracksEditedToTheSameArtistShareOneArtistRow() throws Exception {
		String first = upload(tagged, "one.mp3");
		String second = upload(variantOf(tagged, 0x02), "two.mp3");

		patchTrack(first, """
				{"artistName": "Shared Name", "albumTitle": "Record One"}
				""").andExpect(status().isOk());
		patchTrack(second, """
				{"artistName": "Shared Name", "albumTitle": "Record Two"}
				""").andExpect(status().isOk());

		assertThat(artists.count()).isEqualTo(1);
		assertThat(albums.count()).isEqualTo(2);
	}

	@Test
	void clearingTheAlbumWithAnEmptyStringLeavesTheTrackWithNoAlbum() throws Exception {
		String id = upload(tagged, "tone.mp3");

		patchTrack(id, """
				{"albumTitle": ""}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.albumTitle").doesNotExist());

		assertThat(albums.count()).isZero();
		assertThat(artists.count()).isZero();
	}

	@Test
	void editsAreRecordedAsProvenanceSoEnrichmentCannotOverwriteThem() throws Exception {
		String id = upload(tagged, "tone.mp3");

		patchTrack(id, """
				{"title": "Hand corrected", "trackNumber": 11}
				""").andExpect(status().isOk());

		mockMvc.perform(get("/api/tracks/{id}", id))
				.andExpect(jsonPath("$.userEditedFields").isArray())
				.andExpect(jsonPath("$.userEditedFields", org.hamcrest.Matchers.containsInAnyOrder(
						"title", "trackNumber")));
	}

	@Test
	void aBlankTitleIsRejected() throws Exception {
		String id = upload(tagged, "tone.mp3");

		patchTrack(id, """
				{"title": "   "}
				""").andExpect(status().isBadRequest());
	}

	@Test
	void anImpossibleTrackNumberIsRejected() throws Exception {
		String id = upload(tagged, "tone.mp3");

		patchTrack(id, """
				{"trackNumber": -4}
				""").andExpect(status().isBadRequest());
	}

	@Test
	void editingSomethingThatIsNotThereIsANotFound() throws Exception {
		patchTrack(UUID.randomUUID().toString(), """
				{"title": "nope"}
				""").andExpect(status().isNotFound());
	}
}
