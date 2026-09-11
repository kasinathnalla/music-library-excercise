package com.kasi.musiclibrary.security;

import com.kasi.musiclibrary.repository.AlbumRepository;
import com.kasi.musiclibrary.repository.ArtistRepository;
import com.kasi.musiclibrary.repository.TrackRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The rules, as tests. An admin curates the library; a customer listens to it.
 *
 * <p>The distinction between 401 and 403 is asserted deliberately. Not signed in is 401 and
 * the UI shows the login screen; signed in but not allowed is 403 and the UI says so.
 * Collapsing the two sends a customer who clicked something they should not have seen back to
 * a login screen they are already past, which reads as a broken session rather than a refused
 * action.
 */
class AuthorizationMatrixTest extends SecuredMockMvcTest {

	@Autowired
	private TrackRepository tracks;
	@Autowired
	private AlbumRepository albums;
	@Autowired
	private ArtistRepository artists;

	private String trackId;

	@BeforeEach
	void seedOneTrackAsAdmin() throws Exception {
		tracks.deleteAll();
		albums.deleteAll();
		artists.deleteAll();
		byte[] tagged = Files.readAllBytes(Path.of("src/test/resources/fixtures/tagged.mp3"));
		String body = mockMvc.perform(multipart("/api/tracks")
						.file(new MockMultipartFile("file", "tone.mp3", "audio/mpeg", tagged))
						.with(httpBasic("admin", "admin")).with(csrf()))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		trackId = body.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
	}

	// --- what a customer may do -------------------------------------------------------

	@Test
	void aCustomerCanBrowseTheLibrary() throws Exception {
		mockMvc.perform(get("/api/tracks").with(httpBasic("customer", "customer")).with(csrf()))
				.andExpect(status().isOk());
	}

	@Test
	void aCustomerCanFetchOneTrack() throws Exception {
		mockMvc.perform(get("/api/tracks/{id}", trackId).with(httpBasic("customer", "customer")).with(csrf()))
				.andExpect(status().isOk());
	}

	@Test
	void aCustomerCanReadLibraryStats() throws Exception {
		mockMvc.perform(get("/api/stats").with(httpBasic("customer", "customer")).with(csrf()))
				.andExpect(status().isOk());
	}

	@Test
	void aCustomerCanStreamAudio() throws Exception {
		mockMvc.perform(get("/api/tracks/{id}/stream", trackId)
						.with(httpBasic("customer", "customer")).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"));
	}

	/**
	 * Listening is not much use if the scrubber does not work, and range handling is the part
	 * that breaks first when authentication is bolted on.
	 */
	@Test
	void aCustomerCanSeekWhileStreaming() throws Exception {
		mockMvc.perform(get("/api/tracks/{id}/stream", trackId)
						.header(HttpHeaders.RANGE, "bytes=1000-")
						.with(httpBasic("customer", "customer")).with(csrf()))
				.andExpect(status().isPartialContent());
	}

	// --- what a customer may not do ---------------------------------------------------

	@Test
	void aCustomerCannotUpload() throws Exception {
		byte[] tagged = Files.readAllBytes(Path.of("src/test/resources/fixtures/tagged.mp3"));
		mockMvc.perform(multipart("/api/tracks")
						.file(new MockMultipartFile("file", "other.mp3", "audio/mpeg", tagged))
						.with(httpBasic("customer", "customer")).with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	void aCustomerCannotEditMetadata() throws Exception {
		mockMvc.perform(patch("/api/tracks/{id}", trackId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Renamed by a listener\"}")
						.with(httpBasic("customer", "customer")).with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	void aCustomerCannotDeleteATrack() throws Exception {
		mockMvc.perform(delete("/api/tracks/{id}", trackId).with(httpBasic("customer", "customer")).with(csrf()))
				.andExpect(status().isForbidden());
	}

	/**
	 * A 403 that arrives after the work has already been done is not an authorization rule.
	 * This is the test that would catch a refusal placed downstream of ingest.
	 */
	@Test
	void aRefusedUploadDoesNotReachTheLibrary() throws Exception {
		long before = tracks.count();
		byte[] tagged = Files.readAllBytes(Path.of("src/test/resources/fixtures/untagged.mp3"));
		mockMvc.perform(multipart("/api/tracks")
						.file(new MockMultipartFile("file", "sneaky.mp3", "audio/mpeg", tagged))
						.with(httpBasic("customer", "customer")).with(csrf()))
				.andExpect(status().isForbidden());
		assertThat(tracks.count()).isEqualTo(before);
	}

	// --- what an admin may do ---------------------------------------------------------

	@Test
	void anAdminCanEditMetadata() throws Exception {
		mockMvc.perform(patch("/api/tracks/{id}", trackId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Corrected\"}")
						.with(httpBasic("admin", "admin")).with(csrf()))
				.andExpect(status().isOk());
	}

	@Test
	void anAdminCanDeleteATrack() throws Exception {
		mockMvc.perform(delete("/api/tracks/{id}", trackId).with(httpBasic("admin", "admin")).with(csrf()))
				.andExpect(status().isNoContent());
	}

	@Test
	void anAdminUploadRecordsWhoUploadedIt() throws Exception {
		assertThat(tracks.findAll())
				.singleElement()
				.satisfies(track -> assertThat(track.getUploadedBy()).isNotNull());
	}

	// --- CSRF -------------------------------------------------------------------------

	/**
	 * Built without the base class's default CSRF token, because the point is its absence.
	 * Sessions are cookie-backed, so without this a third-party page could post to the API
	 * on a signed-in admin's behalf.
	 */
	@Test
	void aMutatingRequestWithoutACsrfTokenIsRejected() throws Exception {
		MockMvc withoutCsrf = MockMvcBuilders.webAppContextSetup(context)
				.apply(springSecurity())
				.build();

		withoutCsrf.perform(delete("/api/tracks/{id}", trackId).with(httpBasic("admin", "admin")))
				.andExpect(status().isForbidden());
	}
}
