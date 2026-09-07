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

	private String register(String username, String password) {
		return registerJson(username, password, "Alex", "Rivera", "1994-03-02", "1 Example St");
	}

	private String registerJson(String username, String password, String firstName,
			String lastName, String dateOfBirth, String address) {
		return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\","
				+ "\"firstName\":\"" + firstName + "\",\"lastName\":\"" + lastName + "\","
				+ "\"dateOfBirth\":\"" + dateOfBirth + "\",\"address\":\"" + address + "\"}";
	}

	@Test
	void anyoneCanRegisterAndTheAccountIsACustomer() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(register("alex", "hunter2!")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.username").value("alex"))
				.andExpect(jsonPath("$.role").value("CUSTOMER"))
				.andExpect(jsonPath("$.firstName").value("Alex"))
				.andExpect(jsonPath("$.lastName").value("Rivera"));
	}

	/** Real people sign in with their email address; the field must not reject it. */
	@Test
	void aUsernameThatIsAnEmailAddressIsAccepted() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(register("alex.rivera@example.com", "hunter2!")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.username").value("alex.rivera@example.com"));
	}

	/**
	 * There is no field on the request a caller could set to ADMIN, so this proves that
	 * structurally rather than by a check that could later be forgotten or bypassed.
	 */
	@Test
	void anAttemptToSupplyARoleIsIgnoredNotHonoured() throws Exception {
		String body = "{\"username\":\"riley\",\"password\":\"hunter2!\",\"role\":\"ADMIN\","
				+ "\"firstName\":\"Riley\",\"lastName\":\"Quinn\",\"dateOfBirth\":\"1990-01-01\","
				+ "\"address\":\"1 Example St\"}";
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
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

	/** The whole point of collecting a name: it is what the account bar shows. */
	@Test
	void signingInAfterRegistrationReturnsTheFullName() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerJson("morgan2", "hunter2!", "Morgan", "Lee",
								"1988-07-15", "42 Example Ave")))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/auth/me").with(httpBasic("morgan2", "hunter2!")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firstName").value("Morgan"))
				.andExpect(jsonPath("$.lastName").value("Lee"));
	}

	/** The seeded accounts predate profile fields; /me must not break on their absence. */
	@Test
	void aSeededAccountWithNoProfileHasNoNameInMe() throws Exception {
		mockMvc.perform(get("/api/auth/me").with(httpBasic("customer", "customer")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firstName").doesNotExist())
				.andExpect(jsonPath("$.lastName").doesNotExist());
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

	/**
	 * A validation failure returns the app's own error shape -- not Spring's default
	 * problem-detail body -- with a message a form can actually show someone.
	 */
	@Test
	void aBlankUsernameIsRejected() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(register("", "hunter2!")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Choose a username"))
				.andExpect(jsonPath("$.fieldErrors.username").value("Choose a username"));
	}

	@Test
	void multipleFailuresAreAllReportedAtOnce() throws Exception {
		String body = "{\"username\":\"\",\"password\":\"\",\"firstName\":\"\","
				+ "\"lastName\":\"\",\"address\":\"\"}";
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.username").exists())
				.andExpect(jsonPath("$.fieldErrors.password").exists())
				.andExpect(jsonPath("$.fieldErrors.firstName").exists())
				.andExpect(jsonPath("$.fieldErrors.lastName").exists())
				.andExpect(jsonPath("$.fieldErrors.address").exists())
				.andExpect(jsonPath("$.fieldErrors.dateOfBirth").exists());
	}

	@Test
	void aDuplicateUsernameCarriesAReadableMessage() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(register("harper", "hunter2!")))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(register("harper", "somethingElse!")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("That username is already taken"));
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

	@Test
	void aMissingFirstNameIsRejected() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerJson("casey", "hunter2!", "", "Jones", "1990-01-01", "1 St")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void aDateOfBirthInTheFutureIsRejected() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerJson("drew", "hunter2!", "Drew", "Park", "2999-01-01", "1 St")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void aMissingAddressIsRejected() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerJson("frankie", "hunter2!", "Frankie", "Ng", "1990-01-01", "")))
				.andExpect(status().isBadRequest());
	}
}
