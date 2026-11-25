package com.jobscout.integration;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.jobscout.common.ErrorCode;
import com.jobscout.common.JobScoutException;
import com.jobscout.config.JobScoutProperties;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Reads the workbook from Google Drive using a service account (read-only scope).
 * Share the file with the service account's e-mail. Native Google Sheets are exported as .xlsx.
 */
@Component
@ConditionalOnProperty(name = "jobscout.workbook.source", havingValue = "drive")
public class GoogleDriveWorkbookSource implements WorkbookSource {
    private static final String SHEETS_MIME = "application/vnd.google-apps.spreadsheet";
    private static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final JobScoutProperties.Workbook cfg;

    public GoogleDriveWorkbookSource(JobScoutProperties props) {
        this.cfg = props.workbook();
    }

    @Override
    public WorkbookData fetch() {
        try (InputStream creds = new FileInputStream(cfg.credentialsPath())) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(creds)
                    .createScoped(List.of(DriveScopes.DRIVE_READONLY));
            Drive drive = new Drive.Builder(GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(), new HttpCredentialsAdapter(credentials))
                    .setApplicationName("JobScout").build();
            File meta = drive.files().get(cfg.driveFileId()).setFields("id,name,mimeType,modifiedTime").execute();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (SHEETS_MIME.equals(meta.getMimeType())) {
                drive.files().export(cfg.driveFileId(), XLSX_MIME).executeMediaAndDownloadTo(out);
            } else {
                drive.files().get(cfg.driveFileId()).executeMediaAndDownloadTo(out);
            }
            Instant modified = meta.getModifiedTime() == null ? Instant.now()
                    : Instant.ofEpochMilli(meta.getModifiedTime().getValue());
            return new WorkbookData(out.toByteArray(), "drive:" + meta.getId(), modified);
        } catch (Exception e) {
            throw new JobScoutException(ErrorCode.CONFIG_INVALID, "Google Drive fetch failed: " + e.getMessage(), true);
        }
    }
}
