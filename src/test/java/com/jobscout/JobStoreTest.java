package com.jobscout;

import static org.junit.jupiter.api.Assertions.*;

import com.jobscout.normalization.DateParser.Status;
import com.jobscout.normalization.JobDraft;
import com.jobscout.persistence.JobStore;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JobStoreTest {
    private JobDraft draft(String company, String id, String url, String title, String loc) {
        return new JobDraft(company, company, id, title, loc, null, null, url, null, null, Status.UNKNOWN,
                "Java", "India", 7, Map.of());
    }

    @Test void externalIdWinsOverUrl() {
        var a = draft("A", "123", "https://x/1", "Dev", "Pune");
        var b = draft("A", "123", "https://x/other", "Dev II", "Pune");
        assertEquals(JobStore.dedupeKey(a), JobStore.dedupeKey(b));
    }

    @Test void fallsBackToUrlThenTitleLocation() {
        assertEquals(JobStore.dedupeKey(draft("A", null, "https://x/1", "t", "l")),
                JobStore.dedupeKey(draft("A", " ", "https://x/1", "other", "other")));
        assertEquals(JobStore.dedupeKey(draft("A", null, null, "Java  Dev", "Pune")),
                JobStore.dedupeKey(draft("A", null, null, "java dev", "PUNE")));
    }

    @Test void keysAreScopedPerCompany() {
        assertNotEquals(JobStore.dedupeKey(draft("A", "1", null, "t", "l")), JobStore.dedupeKey(draft("B", "1", null, "t", "l")));
    }
}
