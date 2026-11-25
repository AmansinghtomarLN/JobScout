"""Generates config/jobscout-config.xlsx (sample Autodesk configuration). Requires: pip install openpyxl"""
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment
from openpyxl.utils import get_column_letter

wb = Workbook()
HEAD = PatternFill("solid", fgColor="1F3864")


def sheet(ws, headers, rows, widths=None):
    ws.append(headers)
    for r in rows:
        ws.append(r)
    for c in ws[1]:
        c.font = Font(name="Arial", bold=True, color="FFFFFF")
        c.fill = HEAD
    for row in ws.iter_rows(min_row=2):
        for c in row:
            c.font = Font(name="Arial")
            c.alignment = Alignment(wrap_text=True, vertical="top")
    for i, h in enumerate(headers, 1):
        ws.column_dimensions[get_column_letter(i)].width = (widths or {}).get(h, max(14, len(h) + 4))
    ws.freeze_panes = "A2"


# Selectors are UNVERIFIED starting points. Inspect the live page (DevTools) and correct them.
UNVERIFIED = "UNVERIFIED selectors - inspect the live DOM and fix before relying on results"

ws = wb.active
ws.title = "Companies"
sheet(ws, ["company_id", "company_name", "career_url", "enabled", "interaction_type", "search_profile_id",
           "max_pages", "max_jobs", "page_timeout_seconds", "rate_limit_ms", "adapter_key", "timezone",
           "date_format", "notes"],
      [["AUTODESK", "Autodesk",
        "https://jobs.autodesk.com/careers?query=${keyword}&location=${location}&sort_by=timestamp",
        True, "PLAYWRIGHT", "JAVA_INDIA_7D", 10, 300, 30, 1500, "GENERIC", "Asia/Kolkata", "", UNVERIFIED]],
      {"career_url": 70, "notes": 60})

ws = wb.create_sheet("SearchProfiles")
sheet(ws, ["profile_id", "keyword", "location", "posted_days"], [["JAVA_INDIA_7D", "Java", "India", 7]])

ws = wb.create_sheet("SearchFields")
sheet(ws, ["company_id", "profile_id", "field_key", "selector_type", "selector", "input_action", "input_value",
           "required", "wait_after_action_ms"],
      [["AUTODESK", "JAVA_INDIA_7D", "keyword", "CSS", "input[type='search']", "FILL", "${keyword}", False, 500],
       ["AUTODESK", "JAVA_INDIA_7D", "submit", "ROLE", "button:Search", "CLICK", "", False, 1000]],
      {"selector": 32})

ws = wb.create_sheet("ExtractionFields")
sheet(ws, ["company_id", "field_key", "source_scope", "selector_type", "selector", "attribute", "required", "transform"],
      [["AUTODESK", "job_title", "RESULT_CARD", "CSS", "[class*='position-title']", "textContent", True, "TRIM"],
       ["AUTODESK", "job_url", "RESULT_CARD", "CSS", "self", "href", True, "ABSOLUTE_URL"],
       ["AUTODESK", "location", "RESULT_CARD", "CSS", "[class*='position-location']", "textContent", False, "TRIM"],
       ["AUTODESK", "posted_date", "RESULT_CARD", "CSS", "[class*='position-posted']", "textContent", False, "TRIM,DATE_PARSE"],
       ["AUTODESK", "description", "DETAIL_PAGE", "CSS", "[class*='position-job-description']", "innerText", False, "TRIM"]],
      {"selector": 40})

ws = wb.create_sheet("Pagination")
sheet(ws, ["company_id", "pagination_type", "next_selector", "disabled_selector", "max_pages", "wait_condition",
           "result_card_selector"],
      [["AUTODESK", "NEXT_BUTTON", "button:has-text('Show more positions')", "", 10, "SELECTOR", "a[class*='position-card']"]],
      {"next_selector": 40, "result_card_selector": 36})

ws = wb.create_sheet("README")
for line in [
    "JobScout sample workbook",
    "Edit only the white cells in each sheet; header names must stay unchanged.",
    "Placeholders ${keyword}, ${location}, ${posted_days} are filled from SearchProfiles (also usable in career_url).",
    "selector 'self' (ExtractionFields) means the result card element itself (e.g. the card is the <a>).",
    "ROLE selectors use the format role[:accessible name], e.g. button:Search.",
    "Autodesk row: " + UNVERIFIED + ".",
    "Do not store passwords, cookies or tokens in this file.",
]:
    ws.append([line])
ws.column_dimensions["A"].width = 120
ws["A1"].font = Font(name="Arial", bold=True, size=14)

wb.save("config/jobscout-config.xlsx")
print("written config/jobscout-config.xlsx")
