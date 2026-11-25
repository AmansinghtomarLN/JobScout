package com.jobscout.engine;

import java.util.Map;

public record RawJob(Map<String, String> values, String url) {}
