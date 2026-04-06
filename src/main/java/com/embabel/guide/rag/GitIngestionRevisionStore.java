package com.embabel.guide.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Persists last successfully ingested git commit per absolute repository path (JSON map).
 */
public final class GitIngestionRevisionStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path file;
    private Map<String, String> revisions = new LinkedHashMap<>();
    private boolean dirty;

    public GitIngestionRevisionStore(Path file) {
        this.file = file;
    }

    public void load() {
        revisions = new LinkedHashMap<>();
        dirty = false;
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            revisions = new LinkedHashMap<>(
                    MAPPER.readValue(file.toFile(), new TypeReference<Map<String, String>>() {
                    }));
        } catch (IOException e) {
            revisions = new LinkedHashMap<>();
        }
    }

    public Optional<String> getRevision(String absoluteRepoPath) {
        return Optional.ofNullable(revisions.get(absoluteRepoPath));
    }

    public void putRevision(String absoluteRepoPath, String commit) {
        revisions.put(absoluteRepoPath, commit);
        dirty = true;
    }

    /**
     * Remove stored HEAD for a repo path (e.g. before a deliberate full re-ingest).
     *
     * @return true if an entry existed and was removed
     */
    public boolean removeRevision(String absoluteRepoPath) {
        String removed = revisions.remove(absoluteRepoPath);
        if (removed != null) {
            dirty = true;
            return true;
        }
        return false;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void save() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), revisions);
        dirty = false;
    }
}
