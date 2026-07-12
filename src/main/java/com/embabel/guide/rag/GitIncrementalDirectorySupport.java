package com.embabel.guide.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Runs local {@code git} to detect HEAD and changed paths between two commits for incremental RAG ingestion.
 */
public final class GitIncrementalDirectorySupport {

    private static final Logger logger = LoggerFactory.getLogger(GitIncrementalDirectorySupport.class);

    private GitIncrementalDirectorySupport() {
    }

    public static boolean isGitWorkTree(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        Path dotGit = dir.resolve(".git");
        return Files.isDirectory(dotGit) || Files.isRegularFile(dotGit);
    }

    /**
     * Walks parents from {@code start} until a directory containing {@code .git} is found.
     * Needed because guide profiles often list subdirs (e.g. {@code spdd/canvas}) rather than the repo root.
     */
    public static Optional<Path> findGitWorkTreeRoot(Path start) {
        if (start == null) {
            return Optional.empty();
        }
        Path cur = start.toAbsolutePath().normalize();
        if (!Files.isDirectory(cur)) {
            cur = cur.getParent();
        }
        while (cur != null) {
            if (isGitWorkTree(cur)) {
                return Optional.of(cur);
            }
            cur = cur.getParent();
        }
        return Optional.empty();
    }

    /**
     * Keeps repo-relative paths that lie under {@code configuredDir} (itself under {@code gitRoot}).
     * When {@code configuredDir} is the repo root, all paths are returned.
     */
    public static List<String> filterPathsUnderDirectory(
            Path gitRoot,
            Path configuredDir,
            List<String> repoRelativePaths
    ) {
        if (repoRelativePaths == null || repoRelativePaths.isEmpty()) {
            return List.of();
        }
        Path root = gitRoot.toAbsolutePath().normalize();
        Path dir = configuredDir.toAbsolutePath().normalize();
        if (!dir.startsWith(root)) {
            return List.of();
        }
        String prefix = root.relativize(dir).toString().replace('\\', '/');
        if (prefix.isEmpty() || ".".equals(prefix)) {
            return new ArrayList<>(repoRelativePaths);
        }
        String prefixSlash = prefix.endsWith("/") ? prefix : prefix + "/";
        List<String> out = new ArrayList<>();
        for (String p : repoRelativePaths) {
            if (p == null || p.isBlank()) {
                continue;
            }
            String n = p.replace('\\', '/');
            if (n.equals(prefix) || n.startsWith(prefixSlash)) {
                out.add(n);
            }
        }
        return out;
    }

    public static Optional<String> headCommit(Path repoDir) {
        return runGit(repoDir, List.of("rev-parse", "HEAD"), Duration.ofSeconds(30))
                .filter(s -> !s.isBlank())
                .map(String::trim);
    }

    /**
     * Paths relative to repo root that were added, copied, modified, renamed, or type-changed between the two refs.
     * Excludes pure deletes (RAG chunks for removed files are left until a full re-ingest).
     */
    public static List<String> changedPathsBetween(Path repoDir, String fromRef, String toRef) {
        if (fromRef == null || fromRef.isBlank() || toRef == null || toRef.isBlank()) {
            return List.of();
        }
        Optional<String> out = runGit(repoDir,
                List.of("diff", "--name-only", "--diff-filter=ACMRTUXB", fromRef, toRef),
                Duration.ofMinutes(2));
        if (out.isEmpty()) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        for (String line : out.get().split("\n")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                paths.add(t.replace('\\', '/'));
            }
        }
        return paths;
    }

    private static Optional<String> runGit(Path repoDir, List<String> gitArgs, Duration timeout) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.add("-C");
        cmd.add(repoDir.toAbsolutePath().toString());
        cmd.addAll(gitArgs);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            boolean finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                logger.warn("git timed out: {}", String.join(" ", cmd));
                return Optional.empty();
            }
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (p.exitValue() != 0) {
                logger.warn("git exited {} for {}: {}", p.exitValue(), String.join(" ", cmd), output);
                return Optional.empty();
            }
            return Optional.of(output);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("git failed for {}: {}", String.join(" ", cmd), e.getMessage());
            return Optional.empty();
        }
    }
}
