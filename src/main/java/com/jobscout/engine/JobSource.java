package com.jobscout.engine;

import com.jobscout.configuration.model.ConfigModel.CompanyConfig;

/** A way of obtaining raw jobs for a company. Selected by the workbook's adapter_key (default: Playwright). */
public interface JobSource {
    /** Value of adapter_key this source handles, e.g. GREENHOUSE. */
    String key();

    EngineResult fetch(CompanyConfig company, TaskContext ctx);
}
