package com.jobscout.integration;

import java.time.Instant;

public interface WorkbookSource {
    record WorkbookData(byte[] bytes, String sourceId, Instant modifiedAt) {}

    WorkbookData fetch();
}
