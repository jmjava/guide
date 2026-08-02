package com.embabel.guide.rag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Operator endpoints for shared Neo4j: scoped ContentElement purge and git revision reset.
 * <p>
 * These are {@code permitAll} in {@link com.embabel.guide.chat.security.SecurityConfig} like
 * {@code /api/v1/data/load-references}; do not expose Guide to untrusted networks without a reverse proxy.
 */
@RestController
@RequestMapping("/api/v1/data")
public class RagMaintenanceController {

    private final RagContentMaintenanceService maintenanceService;

    public RagMaintenanceController(RagContentMaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @PostMapping("/content-elements/purge-preview")
    public ResponseEntity<PurgePreviewResponse> purgePreview(@RequestBody PurgePreviewRequest body) {
        int limit = body.sampleLimit() != null ? body.sampleLimit() : 10;
        RagContentMaintenanceService.PurgePreviewResult r = maintenanceService.previewPurge(
                body.uriPrefix(), body.directory(), limit);
        return ResponseEntity.ok(new PurgePreviewResponse(
                r.getAppliedUriPrefix(), r.getMatchCount(), r.getSampleUris()));
    }

    @PostMapping("/content-elements/purge")
    public ResponseEntity<PurgeExecuteResponse> purge(@RequestBody PurgeExecuteRequest body) {
        RagContentMaintenanceService.PurgeExecuteResult r = maintenanceService.executePurge(
                body.uriPrefix(), body.directory(), body.confirm());
        return ResponseEntity.ok(new PurgeExecuteResponse(r.getAppliedUriPrefix(), r.getDeletedCount()));
    }

    @PostMapping("/git-ingestion/revision/reset")
    public ResponseEntity<GitRevisionResetResponse> resetGitRevision(@RequestBody GitRevisionResetRequest body) {
        if (body.directory() == null || body.directory().isBlank()) {
            return ResponseEntity.badRequest().body(new GitRevisionResetResponse(
                    null, false, "directory is required"));
        }
        try {
            RagContentMaintenanceService.GitRevisionResetResult r =
                    maintenanceService.resetGitIngestionRevision(body.directory());
            return ResponseEntity.ok(new GitRevisionResetResponse(
                    r.getAbsolutePath(), r.getRemoved(), r.getMessage()));
        } catch (java.io.IOException e) {
            return ResponseEntity.internalServerError().body(new GitRevisionResetResponse(
                    null, false, "Failed to save revision file: " + e.getMessage()));
        }
    }

    public record PurgePreviewRequest(String uriPrefix, String directory, Integer sampleLimit) {}

    public record PurgePreviewResponse(String appliedUriPrefix, long matchCount, List<String> sampleUris) {}

    public record PurgeExecuteRequest(String uriPrefix, String directory, boolean confirm) {}

    public record PurgeExecuteResponse(String appliedUriPrefix, long deletedCount) {}

    public record GitRevisionResetRequest(String directory) {}

    public record GitRevisionResetResponse(String absolutePath, boolean removed, String message) {}
}
