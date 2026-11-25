package com.jobscout.normalization;

import java.time.LocalDate;
import java.util.Map;

/** Canonical, source-independent job record produced by the normalizer. */
public record JobDraft(String companyId, String companyName, String externalJobId, String title, String location,
                       String description, String employmentType, String canonicalUrl, LocalDate postedDate,
                       String postedDateRaw, DateParser.Status postedDateStatus, String searchKeyword,
                       String searchLocation, int postedDays, Map<String, String> rawValues) {}
