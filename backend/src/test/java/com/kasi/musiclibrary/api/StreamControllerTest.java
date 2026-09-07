package com.kasi.musiclibrary.api;

import com.kasi.musiclibrary.catalog.TrackRepository;
import com.kasi.musiclibrary.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StreamControllerTest extends PostgresIntegrationTest {

	@Autowired
	private WebApplicationContext context;
	@Autowired
	private TrackRepository tracks;

	private MockMvc mockMvc;
	private UUID trackId;
	private int fileSize;

	@BeforeEach
	void setUp() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		tracks.deleteAll();

		byte[] tagged = Files.readAllBytes(Path.of("src/test/resources/fixtures/tagged.mp3"));
		fileSize = tagged.length;

		String body = mockMvc.perform(multipart("/api/tracks")
						.file(new MockMultipartFile("file", "tone.mp3", "audio/mpeg", tagged)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		trackId = UUID.fromString(body.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1"));
	}

	@Test
	void servesTheWholeFileWithTheRightContentType() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/tracks/{id}/stream", trackId))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/mpeg"))
				.andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
				.andReturn();

		assertThat(result.getResponse().getContentAsByteArray()).hasSize(fileSize);
	}

	/**
	 * Without Accept-Ranges and 206 handling the browser plays a track from the start but the
	 * scrubber does nothing, which is worse than an absent feature because it looks broken.
	 */
	@Test
	void honoursARangeRequestSoTheBrowserCanSeek() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/tracks/{id}/stream", trackId)
						.header(HttpHeaders.RANGE, "bytes=0-99"))
				.andExpect(status().isPartialContent())
				.andReturn();

		assertThat(result.getResponse().getContentAsByteArray()).hasSize(100);
		assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_RANGE))
				.isEqualTo("bytes 0-99/" + fileSize);
	}

	@Test
	void servesARangeFromTheMiddleOfTheFile() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/tracks/{id}/stream", trackId)
						.header(HttpHeaders.RANGE, "bytes=1000-1499"))
				.andExpect(status().isPartialContent())
				.andReturn();

		assertThat(result.getResponse().getContentAsByteArray()).hasSize(500);
		assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_RANGE))
				.isEqualTo("bytes 1000-1499/" + fileSize);
	}

	@Test
	void returnsNotFoundForAnUnknownTrack() throws Exception {
		mockMvc.perform(get("/api/tracks/{id}/stream", UUID.randomUUID()))
				.andExpect(status().isNotFound());
	}
}
