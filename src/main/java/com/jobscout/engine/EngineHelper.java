package com.jobscout.engine;

import com.jobscout.configuration.model.ConfigModel.CompanyConfig;
import java.net.URI;

public final class EngineHelper {
    private EngineHelper() {}

    /** Host used for per-domain concurrency limits (placeholders resolved first). */
    public static String host(CompanyConfig c) {
        try {
            String h = URI.create(Templates.url(c.careerUrl(), c.profile())).getHost();
            return h == null ? c.companyId() : h.toLowerCase();
        } catch (RuntimeException e) {
            return c.companyId();
        }
    }
}
