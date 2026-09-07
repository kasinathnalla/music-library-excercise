package com.kasi.musiclibrary.support;

import com.kasi.musiclibrary.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Base class for tests that need a real database.
 *
 * <p>The container is defined as a bean and wired via {@code @ServiceConnection}, so
 * Spring's test context cache keeps one Postgres for the whole run rather than starting
 * one per test class.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class PostgresIntegrationTest {
}
