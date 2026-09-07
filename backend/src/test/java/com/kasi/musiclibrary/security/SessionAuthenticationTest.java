package com.kasi.musiclibrary.security;

import com.kasi.musiclibrary.catalog.TrackRepository;
import com.kasi.musiclibrary.support.SecuredMockMvcTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The reason this application keeps a session at all.
 *
 * <p>The browser's audio element issues its own Range requests for
 * {@code /api/tracks/{id}/stream}. Those requests are made by the browser, not by Angular, so
 * no Authorization header can be attached to them. Signing in with Basic therefore has to
 * leave something behind that the browser sends by itself, and that something is the session
 * cookie. If these tests fail, playback is broken for every signed-in user even though every
 * other test still passes.
 */
class SessionAuthenticationTest extends SecuredMockMvcTest {

	@Autowired
	private TrackRepository tracks;

	private String trackId;

	@BeforeEach
	void seedOneTrack() throws Exception {
		byte[] tagged = Files.readAllBytes(Path.of("src/test/resources/fixtures/tagged.mp3"));
		tracks.deleteAll();
		String body = mockMvc.perform(multipart("/api/tracks")
						.file(new MockMultipartFile("file", "tone.mp3", "audio/mpeg", tagged))
						.with(httpBasic("admin", "admin")).with(csrf()))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		trackId = body.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
	}

	@Test
	void signingInEstablishesASession() throws Exception {
		MockHttpSession session = (MockHttpSession) mockMvc
				.perform(get("/api/auth/me").with(httpBasic("customer", "customer")))
				.andExpect(status().isOk())
				.andReturn().getRequest().getSession(false);

		assertThat(session).isNotNull();
	}

	@Test
	void theSessionAloneAuthenticatesTheAudioRequest() throws Exception {
		MockHttpSession session = (MockHttpSession) mockMvc
				.perform(get("/api/auth/me").with(httpBasic("customer", "customer")))
				.andExpect(status().isOk())
				.andReturn().getRequest().getSession(false);

		// No credentials on this one -- exactly what the <audio> element sends.
		mockMvc.perform(get("/api/tracks/{id}/stream", trackId).session(session))
				.andExpect(status().isOk());
	}

	@Test
	void theSessionAlsoAuthenticatesOrdinaryReads() throws Exception {
		MockHttpSession session = (MockHttpSession) mockMvc
				.perform(get("/api/auth/me").with(httpBasic("customer", "customer")))
				.andExpect(status().isOk())
				.andReturn().getRequest().getSession(false);

		mockMvc.perform(get("/api/tracks").session(session)).andExpect(status().isOk());
	}

	@Test
	void loggingOutEndsTheSession() throws Exception {
		MockHttpSession session = (MockHttpSession) mockMvc
				.perform(get("/api/auth/me").with(httpBasic("customer", "customer")))
				.andExpect(status().isOk())
				.andReturn().getRequest().getSession(false);

		mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
				.andExpect(status().isNoContent());

		assertThat(session.isInvalid()).isTrue();
	}

	@Test
	void aCsrfCookieIsIssuedSoTheBrowserCanMutate() throws Exception {
		Cookie[] cookies = mockMvc
				.perform(get("/api/auth/me").with(httpBasic("admin", "admin")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getCookies();

		assertThat(cookies).anySatisfy(c -> assertThat(c.getName()).isEqualTo("XSRF-TOKEN"));
	}
}
