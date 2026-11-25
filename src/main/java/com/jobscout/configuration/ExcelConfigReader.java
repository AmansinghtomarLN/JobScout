package com.jobscout.configuration;

import static com.jobscout.configuration.model.ConfigModel.*;

import java.io.ByteArrayInputStream;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

/** Parses the workbook sheets: Companies, SearchProfiles, SearchFields, ExtractionFields, Pagination. */
@Component
public class ExcelConfigReader {

    public ParsedConfig read(byte[] bytes) {
        List<ConfigIssue> issues = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            List<Map<String, String>> companies = table(wb, "Companies", issues, true);
            List<Map<String, String>> profileRows = table(wb, "SearchProfiles", issues, true);
            List<Map<String, String>> searchRows = table(wb, "SearchFields", issues, false);
            List<Map<String, String>> extractionRows = table(wb, "ExtractionFields", issues, true);
            List<Map<String, String>> paginationRows = table(wb, "Pagination", issues, false);

            Map<String, SearchProfile> profiles = new HashMap<>();
            for (var r : profileRows) {
                String id = str(r, "profile_id");
                if (id == null) continue;
                profiles.put(id, new SearchProfile(id, str(r, "keyword"), str(r, "location"), num(r, "posted_days", 7)));
            }

            List<SearchFieldConfig> searchFields = new ArrayList<>();
            for (var r : searchRows) {
                String cid = str(r, "company_id");
                if (cid == null) continue;
                searchFields.add(new SearchFieldConfig(cid, str(r, "profile_id"), str(r, "field_key"),
                        en(SelectorType.class, str(r, "selector_type"), SelectorType.CSS, issues, "SearchFields", cid),
                        str(r, "selector"),
                        en(InputAction.class, str(r, "input_action"), InputAction.FILL, issues, "SearchFields", cid),
                        str(r, "input_value"), bool(r, "required", false), num(r, "wait_after_action_ms", 500)));
            }

            List<ExtractionFieldConfig> extraction = new ArrayList<>();
            for (var r : extractionRows) {
                String cid = str(r, "company_id");
                if (cid == null) continue;
                extraction.add(new ExtractionFieldConfig(cid, str(r, "field_key"),
                        en(FieldScope.class, str(r, "source_scope"), FieldScope.RESULT_CARD, issues, "ExtractionFields", cid),
                        en(SelectorType.class, str(r, "selector_type"), SelectorType.CSS, issues, "ExtractionFields", cid),
                        str(r, "selector"), str(r, "attribute"), bool(r, "required", false), str(r, "transform")));
            }

            Map<String, PaginationConfig> pagination = new HashMap<>();
            for (var r : paginationRows) {
                String cid = str(r, "company_id");
                if (cid == null) continue;
                pagination.put(cid, new PaginationConfig(
                        en(PaginationType.class, str(r, "pagination_type"), PaginationType.NEXT_BUTTON, issues, "Pagination", cid),
                        str(r, "next_selector"), str(r, "disabled_selector"), num(r, "max_pages", 0),
                        en(WaitCondition.class, str(r, "wait_condition"), WaitCondition.SELECTOR, issues, "Pagination", cid),
                        str(r, "result_card_selector")));
            }

            List<CompanyConfig> out = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (var r : companies) {
                String id = str(r, "company_id");
                if (id == null) continue;
                if (!seen.add(id)) {
                    issues.add(new ConfigIssue(Severity.ERROR, id, "Companies", "Duplicate company_id"));
                    continue;
                }
                String profileId = str(r, "search_profile_id");
                PaginationConfig pg = pagination.getOrDefault(id,
                        new PaginationConfig(PaginationType.NONE, null, null, 0, WaitCondition.SELECTOR, null));
                int maxPages = num(r, "max_pages", pg.maxPages() > 0 ? pg.maxPages() : 10);
                out.add(new CompanyConfig(id, str(r, "company_name") == null ? id : str(r, "company_name"),
                        str(r, "career_url"), bool(r, "enabled", true),
                        en(InteractionType.class, str(r, "interaction_type"), InteractionType.PLAYWRIGHT, issues, "Companies", id),
                        profileId, maxPages, num(r, "max_jobs", 500), num(r, "page_timeout_seconds", 30),
                        num(r, "rate_limit_ms", 1500), str(r, "adapter_key"),
                        str(r, "timezone") == null ? "UTC" : str(r, "timezone"), str(r, "date_format"),
                        profileId == null ? null : profiles.get(profileId),
                        searchFields.stream().filter(s -> s.companyId().equals(id)
                                && (s.profileId() == null || profileId == null || s.profileId().equals(profileId))).toList(),
                        extraction.stream().filter(e -> e.companyId().equals(id)).toList(), pg));
            }
            return new ParsedConfig(out, issues);
        } catch (Exception e) {
            issues.add(new ConfigIssue(Severity.ERROR, null, "Workbook", "Cannot read workbook: " + e.getMessage()));
            return new ParsedConfig(List.of(), issues);
        }
    }

    private List<Map<String, String>> table(Workbook wb, String name, List<ConfigIssue> issues, boolean required) {
        Sheet sh = wb.getSheet(name);
        if (sh == null) {
            issues.add(new ConfigIssue(required ? Severity.ERROR : Severity.WARN, null, name, "Sheet '" + name + "' not found"));
            return List.of();
        }
        DataFormatter fmt = new DataFormatter(Locale.ENGLISH);
        FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
        Row header = sh.getRow(sh.getFirstRowNum());
        if (header == null) return List.of();
        List<String> cols = new ArrayList<>();
        for (int c = 0; c < header.getLastCellNum(); c++) {
            Cell hc = header.getCell(c);
            cols.add(hc == null ? "" : fmt.formatCellValue(hc, ev).trim().toLowerCase(Locale.ROOT));
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = sh.getFirstRowNum() + 1; i <= sh.getLastRowNum(); i++) {
            Row row = sh.getRow(i);
            if (row == null) continue;
            Map<String, String> m = new LinkedHashMap<>();
            boolean any = false;
            for (int c = 0; c < cols.size(); c++) {
                Cell cell = row.getCell(c);
                String v = cell == null ? "" : fmt.formatCellValue(cell, ev).trim();
                if (!v.isEmpty()) any = true;
                m.put(cols.get(c), v);
            }
            if (any) rows.add(m);
        }
        return rows;
    }

    private static String str(Map<String, String> r, String k) {
        String v = r.get(k);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static boolean bool(Map<String, String> r, String k, boolean def) {
        String v = str(r, k);
        if (v == null) return def;
        return v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes") || v.equals("1");
    }

    private static int num(Map<String, String> r, String k, int def) {
        String v = str(r, k);
        if (v == null) return def;
        try { return (int) Double.parseDouble(v); } catch (NumberFormatException e) { return def; }
    }

    private static <E extends Enum<E>> E en(Class<E> type, String v, E def, List<ConfigIssue> issues, String sheet, String cid) {
        if (v == null) return def;
        try { return Enum.valueOf(type, v.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) {
            issues.add(new ConfigIssue(Severity.ERROR, cid, sheet, "Invalid " + type.getSimpleName() + " value '" + v + "'"));
            return def;
        }
    }
}
