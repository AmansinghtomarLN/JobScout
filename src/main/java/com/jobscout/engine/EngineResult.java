package com.jobscout.engine;

import java.util.List;

public record EngineResult(List<RawJob> jobs, int pagesVisited) {}
