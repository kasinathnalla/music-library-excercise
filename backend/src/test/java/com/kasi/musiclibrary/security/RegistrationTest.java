package com.kasi.musiclibrary.security;

import com.kasi.musiclibrary.support.SecuredMockMvcTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Self-registration, and why it can only ever produce a customer.
 *
 * <p>The endpoint is reachable with no session (registering is how a session becomes possible
 * at all), so the base class's default CSRF token stands in for the cookie a real browser
 * would already be holding from an earlier page load -- see D16 for why that cookie exists at
 * all.
 */
class RegistrationTest extends SecuredMockMvcTest {

	@Autowired
	private AppUserRepository users;

	private String register(String username, String password) throws Exception {
		return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
	}

	@Test
	void anyoneCanRegisterAndTheAccountIsACustomer() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(register("alex", "hunter2!")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.username").value("alex"))
				.andExpect(jsonPath("$.role").value("CUSTOMER"));
	}

	/**
	 * There is no field on the request a caller could set to ADMIN, so this proves that
	 * structurally rather than by a check that could later be forgotten or bypassed.
	 */
	@Test
	void anAttemptToSupplyARoleIsIgnoredNotHonoured() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"riley\",\"password\":\"hunter2!\",\"role\":\"ADMIN\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("CUSTOMER"));

		assertThat(users.findByUsernameIgnoreCase("riley")).get()
				.extracting(AppUser::getRole).isEqualTo(Role.CUSTOMER);
	}

	@Test
	void theNewAccountCanSignInImmediately() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(register("jordan", "hunter2!")))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/auth/me").with(httpBasic("jordan", "hunter2!")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("CUSTOMER"));
	}

	@Test
	void aDuplicateUsernameIsRejected() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(register("morgan", "hunter2!")))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(register("morgan", "somethingElse!")))
				.andExpect(status().isConflict());
	}

	@Test
	void aDuplicateUsernameIsRejectedCaseInsensitively() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(register("Taylor", "hunter2!")))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(register("taylor", "somethingElse!")))
				.andExpect(status().isConflict());
	}

	/** The seeded admin username is not special-cased, but it is already taken. */
	@Test
	void registeringTheSeededAdminUsernameIsRejected() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(register("admin", "hunter2!")))
				.andExpect(status().isConflict());
	}

	@Test
	void aBlankUsernameIsRejected() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(register("", "hunter2!")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void aShortPasswordIsRejected() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(register("sam", "abc")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void aUsernameWithDisallowedCharactersIsRejected() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(register("bad name!", "hunter2!")))
				.andExpect(status().isBadRequest());
	}
}
