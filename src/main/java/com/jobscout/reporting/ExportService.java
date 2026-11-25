package com.jobscout.reporting;

import com.jobscout.api.JobFilter;
import com.jobscout.persistence.entity.JobPosting;
import com.jobscout.persistence.repository.JobPostingRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class ExportService {
    private static final int MAX_ROWS = 50_000;
    private static final String[] HEADERS = {"Company", "Job ID", "Title", "Location", "Posted date", "Posted (raw)",
            "Date status", "Status", "First seen", "Last seen", "Last run", "URL"};
    private final JobPostingRepository jobs;

    public ExportService(JobPostingRepository jobs) {
        this.jobs = jobs;
    }

    public byte[] toExcel(JobFilter filter) {
        List<JobPosting> rows = jobs.findAll(filter.toSpec(),
                PageRequest.of(0, MAX_ROWS, Sort.by(Sort.Direction.DESC, "postedDate", "id"))).getContent();
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("Jobs");
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            Row h = sh.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(head);
            }
            int r = 1;
            for (JobPosting j : rows) {
                Row row = sh.createRow(r++);
                String[] v = {j.companyName, j.externalJobId, j.title, j.location, str(j.postedDate), j.postedDateRaw,
                        j.postedDateStatus, j.currentStatus, str(j.firstSeenAt), str(j.lastSeenAt), str(j.lastRunId), j.canonicalUrl};
                for (int i = 0; i < v.length; i++) if (v[i] != null) row.createCell(i).setCellValue(v[i]);
            }
            sh.createFreezePane(0, 1);
            sh.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, r - 1), 0, HEADERS.length - 1));
            for (int i = 0; i < HEADERS.length; i++) sh.setColumnWidth(i, i == 2 || i == 11 ? 12000 : 5000);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Export failed", e);
        }
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
}
