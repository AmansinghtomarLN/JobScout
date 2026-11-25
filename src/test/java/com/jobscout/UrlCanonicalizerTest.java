package com.jobscout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jobscout.normalization.UrlCanonicalizer;
import org.junit.jupiter.api.Test;

class UrlCanonicalizerTest {
    @Test void stripsTrackingFragmentAndTrailingSlash() {
        assertEquals("https://jobs.example.com/careers/job/123?pid=9",
                UrlCanonicalizer.canonicalize("HTTPS://Jobs.Example.com/careers/job/123/?utm_source=x&pid=9&gclid=abc#apply"));
    }

    @Test void resolvesRelativeLinks() {
        assertEquals("https://jobs.example.com/careers/job/55",
                UrlCanonicalizer.absolute("https://jobs.example.com/careers?q=java", "/careers/job/55"));
    }
}
