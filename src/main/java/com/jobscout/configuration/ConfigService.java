package com.jobscout.configuration;

import static com.jobscout.configuration.model.ConfigModel.*;

import com.jobscout.integration.WorkbookSource;
import com.jobscout.integration.WorkbookSource.WorkbookData;
import com.jobscout.persistence.entity.ConfigSnapshot;
import com.jobscout.persistence.repository.ConfigSnapshotRepository;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Imports the workbook, validates it and keeps parsed snapshots (referenced by id from every run). */
@Service
public class ConfigService {

    public record LoadedConfig(long snapshotId, String checksum, String source, Instant importedAt,
                               List<CompanyConfig> companies, List<ConfigIssue> issues) {
        public List<ConfigIssue> errorsFor(String companyId) {
            return issues.stream().filter(i -> i.severity() == Severity.ERROR && companyId.equals(i.companyId())).toList();
        }
        public boolean hasGlobalErrors() {
            return issues.stream().anyMatch(i -> i.severity() == Severity.ERROR && i.companyId() == null);
        }
    }

    private final WorkbookSource source;
    private final ExcelConfigReader reader;
    private final ConfigValidator validator;
    private final ConfigSnapshotRepository repo;
    private final Map<Long, LoadedConfig> snapshots = new ConcurrentHashMap<>();
    private volatile LoadedConfig current;

    public ConfigService(WorkbookSource source, ExcelConfigReader reader, ConfigValidator validator,
                         ConfigSnapshotRepository repo) {
        this.source = source;
        this.reader = reader;
        this.validator = validator;
        this.repo = repo;
    }

    public synchronized LoadedConfig sync() {
        WorkbookData data = source.fetch();
        String checksum = sha256(data.bytes());
        if (current != null && current.checksum().equals(checksum)) return current;

        ParsedConfig parsed = reader.read(data.bytes());
        List<ConfigIssue> issues = new ArrayList<>(parsed.issues());
        issues.addAll(validator.validate(parsed.companies()));

        ConfigSnapshot s = new ConfigSnapshot();
        s.source = "workbook";
        s.fileId = data.sourceId();
        s.checksum = checksum;
        s.modifiedAt = data.modifiedAt();
        s.importedAt = Instant.now();
        s.companyCount = parsed.companies().size();
        s = repo.save(s);

        LoadedConfig loaded = new LoadedConfig(s.id, checksum, data.sourceId(), s.importedAt, parsed.companies(), issues);
        snapshots.put(s.id, loaded);
        current = loaded;
        return loaded;
    }

    public LoadedConfig current() {
        LoadedConfig c = current;
        return c != null ? c : sync();
    }

    public Optional<LoadedConfig> snapshot(long id) {
        return Optional.ofNullable(snapshots.get(id));
    }

    private static String sha256(byte[] b) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
