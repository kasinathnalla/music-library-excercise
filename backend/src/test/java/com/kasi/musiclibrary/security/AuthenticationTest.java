package com.kasi.musiclibrary.security;

import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who gets in at all. What they may do once in is {@link AuthorizationMatrixTest}.
 */
class AuthenticationTest extends SecuredMockMvcTest {

	@Test
	void anonymousBrowsingIsRejected() throws Exception {
		mockMvc.perform(get("/api/tracks")).andExpect(status().isUnauthorized());
	}

	@Test
	void anonymousStatsAreRejected() throws Exception {
		mockMvc.perform(get("/api/stats")).andExpect(status().isUnauthorized());
	}

	@Test
	void anonymousStreamingIsRejected() throws Exception {
		mockMvc.perform(get("/api/tracks/{id}/stream", java.util.UUID.randomUUID()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void theSeededAdminCanSignIn() throws Exception {
		mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "admin")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("admin"))
				.andExpect(jsonPath("$.role").value("ADMIN"));
	}

	@Test
	void theSeededCustomerCanSignIn() throws Exception {
		mockMvc.perform(get("/api/auth/me").with(httpBasic("customer", "customer")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("customer"))
				.andExpect(jsonPath("$.role").value("CUSTOMER"));
	}

	@Test
	void aUsernameIsMatchedCaseInsensitively() throws Exception {
		mockMvc.perform(get("/api/auth/me").with(httpBasic("ADMIN", "admin")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMIN"));
	}

	@Test
	void theWrongPasswordIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "nope")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anUnknownUserIsUnauthorizedAndIndistinguishableFromABadPassword() throws Exception {
		mockMvc.perform(get("/api/auth/me").with(httpBasic("nobody", "nope")))
				.andExpect(status().isUnauthorized());
	}

	/**
	 * A WWW-Authenticate header makes the browser pop its own credential dialog over the SPA,
	 * which cannot then be dismissed or signed out of. The SPA owns the login experience.
	 */
	@Test
	void aRejectedRequestDoesNotChallengeTheBrowser() throws Exception {
		mockMvc.perform(get("/api/tracks"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().doesNotExist("WWW-Authenticate"));
	}

	@Test
	void theHealthEndpointStaysOpen() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void theApiDocsStayOpenSoTheApiCanBeReadWithoutAnAccount() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
	}
}
