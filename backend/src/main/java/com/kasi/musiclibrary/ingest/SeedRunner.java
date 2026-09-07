package com.kasi.musiclibrary.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import java.io.InputStream;

@Component
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);
    private static final String SEED_PATTERN = "classpath:seed-audio/*.mp3";

    private final IngestService ingest;
    private final boolean enabled;

    public SeedRunner(IngestService ingest,
                      @Value("${musiclibrary.seed.enabled:true}") boolean enabled) {
        this.ingest = ingest;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (enabled) {
            seed();
        }
    }

    public void seed() {
        int added = 0;
        int alreadyPresent = 0;
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(SEED_PATTERN);
            for (Resource resource : resources) {
                try (InputStream stream = resource.getInputStream()) {
                    ingest.ingest(stream, resource.getFilename());
                    added++;
                } catch (DuplicateTrackException e) {
                    alreadyPresent++;
                } catch (Exception e) {
                    log.warn("Could not seed {}: {}", resource.getFilename(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Seed audio could not be listed: {}", e.getMessage());
            return;
        }
        log.info("Seed complete: {} added, {} already present", added, alreadyPresent);
    }
}
