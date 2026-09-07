package com.kasi.musiclibrary.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Base for every test that goes through HTTP.
 *
 * <p>The {@code springSecurity()} configurer is the whole point of this class. Without it
 * MockMvc builds a chain with no security filters at all, so an unauthenticated request
 * succeeds and every authorization test passes for the wrong reason. That failure mode is
 * exactly the one AGENTS.md records for Testcontainers silently overriding application.yaml:
 * a green suite proving nothing. To check this is still wired, comment out the configurer and
 * confirm {@code AuthenticationTest} goes red.
 *
 * <p>The default request attaches a CSRF token so that individual tests do not each have to.
 * CSRF is genuinely enforced -- {@code AuthorizationMatrixTest.aMutatingRequestWithoutACsrfTokenIsRejected}
 * builds its own MockMvc without this default and asserts the rejection.
 */
public abstract class SecuredMockMvcTest extends PostgresIntegrationTest {

	@Autowired
	protected WebApplicationContext context;

	protected MockMvc mockMvc;

	@BeforeEach
	void buildSecuredMockMvc() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.apply(springSecurity())
				.defaultRequest(get("/").with(csrf()))
				.build();
	}
}
