package com.jobscout.engine;

import static com.jobscout.configuration.model.ConfigModel.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdapterParsingTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private CompanyConfig company(String keyword, String location) {
        return new CompanyConfig("ACME", "Acme", "https://boards.greenhouse.io/acme", true, InteractionType.PLAYWRIGHT,
                "P1", 5, 100, 30, 0, "GREENHOUSE", "UTC", null, new SearchProfile("P1", keyword, location, 7),
                List.of(), List.of(), new PaginationConfig(PaginationType.NONE, null, null, 0, WaitCondition.SELECTOR, null));
    }

    @Test void greenhouseFiltersByKeywordAndLocation() throws Exception {
        var root = mapper.readTree("""
            {"jobs":[
              {"id":1,"title":"Senior Java Developer","absolute_url":"https://x/1","first_published":"2026-11-24T10:00:00-05:00",
               "location":{"name":"Bengaluru, India"},"content":"&lt;p&gt;Spring Boot&lt;/p&gt;"},
              {"id":2,"title":"Designer","absolute_url":"https://x/2","updated_at":"2026-11-20T10:00:00Z",
               "location":{"name":"Bengaluru, India"},"content":"Figma"},
              {"id":3,"title":"Java Engineer","absolute_url":"https://x/3","location":{"name":"Berlin, Germany"},"content":""}
            ]}""");
        var jobs = GreenhouseSource.parse(root, company("Java", "India"));
        assertEquals(1, jobs.size());
        assertEquals("Senior Java Developer", jobs.get(0).values().get("job_title"));
        assertEquals("2026-11-24T10:00:00-05:00", jobs.get(0).values().get("posted_date"));
        assertEquals("Spring Boot", jobs.get(0).values().get("description"));
    }

    @Test void leverMapsFieldsAndEpochDate() throws Exception {
        var root = mapper.readTree("""
            [{"id":"abc","text":"Java Backend Engineer","hostedUrl":"https://jobs.lever.co/acme/abc",
              "createdAt":1795564800000,"categories":{"location":"Pune, India","commitment":"Full-time"},
              "descriptionPlain":"Build services"}]""");
        var jobs = LeverSource.parse(root, company("java", null));
        assertEquals(1, jobs.size());
        assertEquals("1795564800000", jobs.get(0).values().get("posted_date"));
        assertEquals("Full-time", jobs.get(0).values().get("employment_type"));
    }

    @Test void boardTokenFromUrl() {
        assertEquals("acme", HttpJson.boardToken("https://boards.greenhouse.io/acme/jobs/123"));
        assertThrows(RuntimeException.class, () -> HttpJson.boardToken("https://jobs.example.com/"));
    }
}
