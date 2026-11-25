package com.jobscout;

import static com.jobscout.configuration.model.ConfigModel.*;
import static org.junit.jupiter.api.Assertions.*;

import com.jobscout.configuration.ExcelConfigReader;
import java.io.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelConfigReaderTest {

    private static void row(Sheet s, int i, String... v) {
        Row r = s.createRow(i);
        for (int c = 0; c < v.length; c++) r.createCell(c).setCellValue(v[c]);
    }

    @Test void parsesAllSheetsIntoTypedConfig() throws Exception {
        byte[] bytes;
        try (Workbook wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            row(wb.createSheet("Companies"), 0, "company_id", "company_name", "career_url", "enabled", "interaction_type", "search_profile_id", "max_pages");
            row(wb.getSheet("Companies"), 1, "ACME", "Acme", "https://jobs.acme.test", "TRUE", "PLAYWRIGHT", "P1", "3");
            row(wb.createSheet("SearchProfiles"), 0, "profile_id", "keyword", "location", "posted_days");
            row(wb.getSheet("SearchProfiles"), 1, "P1", "Java", "India", "7");
            row(wb.createSheet("SearchFields"), 0, "company_id", "profile_id", "field_key", "selector_type", "selector", "input_action", "input_value", "required");
            row(wb.getSheet("SearchFields"), 1, "ACME", "P1", "keyword", "CSS", "input#q", "FILL", "${keyword}", "TRUE");
            row(wb.createSheet("ExtractionFields"), 0, "company_id", "field_key", "source_scope", "selector_type", "selector", "attribute", "required", "transform");
            row(wb.getSheet("ExtractionFields"), 1, "ACME", "job_title", "RESULT_CARD", "CSS", "h2", "textContent", "TRUE", "TRIM");
            row(wb.createSheet("Pagination"), 0, "company_id", "pagination_type", "next_selector", "max_pages", "wait_condition", "result_card_selector");
            row(wb.getSheet("Pagination"), 1, "ACME", "NEXT_BUTTON", "a.next", "10", "SELECTOR", "div.card");
            wb.write(out);
            bytes = out.toByteArray();
        }
        ParsedConfig parsed = new ExcelConfigReader().read(bytes);
        assertEquals(1, parsed.companies().size());
        CompanyConfig c = parsed.companies().get(0);
        assertEquals(3, c.maxPages());
        assertEquals(7, c.profile().postedDays());
        assertEquals(1, c.searchFields().size());
        assertEquals(InputAction.FILL, c.searchFields().get(0).action());
        assertEquals("div.card", c.pagination().resultCardSelector());
        assertTrue(parsed.issues().isEmpty(), parsed.issues().toString());
    }

    @Test void reportsMissingRequiredSheets() throws Exception {
        byte[] bytes;
        try (Workbook wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            wb.createSheet("Other");
            wb.write(out);
            bytes = out.toByteArray();
        }
        ParsedConfig parsed = new ExcelConfigReader().read(bytes);
        assertTrue(parsed.issues().stream().anyMatch(i -> i.severity() == Severity.ERROR && "Companies".equals(i.sheet())));
    }
}
