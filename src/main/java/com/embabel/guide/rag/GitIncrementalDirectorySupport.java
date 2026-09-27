package com.embabel.guide.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Runs local {@code git} to detect HEAD and changed paths between two commits for incremental RAG ingestion.
 * When both refs are the current HEAD, staged, unstaged, and untracked paths are part of the change set.
 * A dirty work tree is never reported as an empty change set. If git cannot list those paths, incremental
 * ingest is refused.
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
     * <p>
     * When {@code fromRef} and {@code toRef} both resolve to the current HEAD, also returns uncommitted paths
     * (staged, unstaged, and untracked, excluding ignored files). That result is empty only when the work tree
     * is clean. If those refs are HEAD and git cannot list uncommitted paths, throws
     * {@link IllegalStateException} instead of reporting an empty change set.
     */
    public static List<String> changedPathsBetween(Path repoDir, String fromRef, String toRef) {
        if (fromRef == null || fromRef.isBlank() || toRef == null || toRef.isBlank()) {
            return List.of();
        }
        List<String> committed = nameOnlyDiff(repoDir,
                List.of("diff", "--name-only", "--diff-filter=ACMRTUXB", fromRef, toRef),
                Duration.ofMinutes(2));
        if (!refsAreUnchangedHead(repoDir, fromRef, toRef)) {
            return committed;
        }
        LinkedHashSet<String> paths = new LinkedHashSet<>(committed);
        paths.addAll(uncommittedPaths(repoDir));
        return new ArrayList<>(paths);
    }

    /**
     * True when both refs resolve to the current HEAD. Equal ref strings that cannot be resolved refuse
     * incremental ingest: a dirty tree must not be reported as clean.
     */
    private static boolean refsAreUnchangedHead(Path repoDir, String fromRef, String toRef) {
        Optional<String> head = headCommit(repoDir);
        Optional<String> from = resolveCommit(repoDir, fromRef);
        Optional<String> to = resolveCommit(repoDir, toRef);
        if (head.isEmpty() || from.isEmpty() || to.isEmpty()) {
            if (fromRef.trim().equals(toRef.trim())) {
                throw new IllegalStateException(
                        "refusing incremental ingest: cannot verify uncommitted changes while HEAD is unchanged in "
                                + repoDir.toAbsolutePath());
            }
            return false;
        }
        return head.get().equals(from.get()) && head.get().equals(to.get());
    }

    private static Optional<String> resolveCommit(Path repoDir, String ref) {
        return runGit(repoDir, List.of("rev-parse", "--verify", ref + "^{commit}"), Duration.ofSeconds(30))
                .filter(s -> !s.isBlank())
                .map(String::trim);
    }

    /**
     * Staged, unstaged, and untracked paths relative to HEAD. Ignored files are omitted. Pure deletes are omitted.
     * Git failure throws rather than looking like a clean tree.
     */
    private static List<String> uncommittedPaths(Path repoDir) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        paths.addAll(requireNameOnly(repoDir,
                List.of("diff", "--name-only", "--diff-filter=ACMRTUXB", "HEAD"),
                "working tree diff against HEAD"));
        paths.addAll(requireNameOnly(repoDir,
                List.of("ls-files", "--others", "--exclude-standard"),
                "untracked files"));
        return new ArrayList<>(paths);
    }

    private static List<String> nameOnlyDiff(Path repoDir, List<String> gitArgs, Duration timeout) {
        Optional<String> out = runGit(repoDir, gitArgs, timeout);
        if (out.isEmpty()) {
            return List.of();
        }
        return parseNameOnly(out.get());
    }

    private static List<String> requireNameOnly(Path repoDir, List<String> gitArgs, String what) {
        Optional<String> out = runGit(repoDir, gitArgs, Duration.ofMinutes(2));
        if (out.isEmpty()) {
            throw new IllegalStateException(
                    "refusing incremental ingest: git failed while listing " + what + " in "
                            + repoDir.toAbsolutePath());
        }
        return parseNameOnly(out.get());
    }

    private static List<String> parseNameOnly(String output) {
        List<String> paths = new ArrayList<>();
        for (String line : output.split("\n")) {
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
