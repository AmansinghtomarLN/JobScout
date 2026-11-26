package com.jobscout.engine;

import com.jobscout.configuration.model.ConfigModel.CompanyConfig;
import java.util.List;
import org.springframework.stereotype.Component;

/** Picks an API adapter by adapter_key; anything else uses the generic Playwright engine. */
@Component
public class JobSourceRouter {
    private final List<JobSource> adapters;
    private final PlaywrightJobEngine playwright;

    public JobSourceRouter(List<JobSource> adapters, PlaywrightJobEngine playwright) {
        this.adapters = adapters;
        this.playwright = playwright;
    }

    public boolean isAdapter(String adapterKey) {
        return find(adapterKey) != null;
    }

    public EngineResult run(CompanyConfig c, TaskContext ctx) {
        JobSource s = find(c.adapterKey());
        return s != null ? s.fetch(c, ctx) : playwright.run(c, ctx);
    }

    private JobSource find(String key) {
        if (key == null) return null;
        return adapters.stream().filter(a -> a.key().equalsIgnoreCase(key.trim())).findFirst().orElse(null);
    }
}
