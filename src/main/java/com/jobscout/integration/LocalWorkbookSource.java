package com.jobscout.integration;

import com.jobscout.common.ErrorCode;
import com.jobscout.common.JobScoutException;
import com.jobscout.config.JobScoutProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "jobscout.workbook.source", havingValue = "local", matchIfMissing = true)
public class LocalWorkbookSource implements WorkbookSource {
    private final Path path;

    public LocalWorkbookSource(JobScoutProperties props) {
        this.path = Path.of(props.workbook().localPath());
    }

    @Override
    public WorkbookData fetch() {
        try {
            return new WorkbookData(Files.readAllBytes(path), path.toAbsolutePath().toString(),
                    Files.getLastModifiedTime(path).toInstant());
        } catch (IOException e) {
            throw new JobScoutException(ErrorCode.CONFIG_INVALID, "Cannot read workbook " + path + ": " + e.getMessage(), false);
        }
    }
}
