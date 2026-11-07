package com.embabel.guide.rag;

import com.embabel.agent.rag.ingestion.FetchRoute;
import com.embabel.agent.rag.ingestion.HierarchicalContentReader;
import com.embabel.agent.rag.model.NavigableDocument;
import com.embabel.agent.rag.store.ChunkingContentElementRepository;
import com.embabel.guide.ContentConfig;
import com.embabel.guide.GuideProperties;
import com.embabel.guide.VersionedContentConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitIncrementalDirectorySupportTest {

    @TempDir
    Path repo;

    @Test
    void headAndChangedFilesBetweenCommits() throws Exception {
        Assumptions.assumeTrue(gitAvailable(), "git must be on PATH");

        run(repo, "git", "init");
        run(repo, "git", "config", "user.email", "test@test.local");
        run(repo, "git", "config", "user.name", "Test");
        Files.writeString(repo.resolve("a.txt"), "v1", StandardCharsets.UTF_8);
        run(repo, "git", "add", "a.txt");
        run(repo, "git", "commit", "-m", "first");
        String first = GitIncrementalDirectorySupport.headCommit(repo).orElseThrow();

        Files.writeString(repo.resolve("b.txt"), "new", StandardCharsets.UTF_8);
        run(repo, "git", "add", "b.txt");
        run(repo, "git", "commit", "-m", "second");
        String second = GitIncrementalDirectorySupport.headCommit(repo).orElseThrow();

        assertThat(first).isNotEqualTo(second);
        assertThat(GitIncrementalDirectorySupport.isGitWorkTree(repo)).isTrue();

        List<String> changed = GitIncrementalDirectorySupport.changedPathsBetween(repo, first, second);
        assertThat(changed).containsExactly("b.txt");
    }

    @Test
    void unchangedHeadOnDirtyWorkTreeIsNotAnEmptyChangeSet() throws Exception {
        Assumptions.assumeTrue(gitAvailable(), "git must be on PATH");

        run(repo, "git", "init");
        run(repo, "git", "config", "user.email", "test@test.local");
        run(repo, "git", "config", "user.name", "Test");
        Files.writeString(repo.resolve("a.txt"), "v1", StandardCharsets.UTF_8);
        run(repo, "git", "add", "a.txt");
        run(repo, "git", "commit", "-m", "first");
        String head = GitIncrementalDirectorySupport.headCommit(repo).orElseThrow();

        assertThat(GitIncrementalDirectorySupport.changedPathsBetween(repo, head, head)).isEmpty();

        Files.writeString(repo.resolve("a.txt"), "dirty", StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("staged.txt"), "staged", StandardCharsets.UTF_8);
        run(repo, "git", "add", "staged.txt");
        Files.writeString(repo.resolve("new.txt"), "untracked", StandardCharsets.UTF_8);

        assertThat(GitIncrementalDirectorySupport.headCommit(repo)).contains(head);
        assertThat(GitIncrementalDirectorySupport.changedPathsBetween(repo, head, head))
                .containsExactlyInAnyOrder("a.txt", "staged.txt", "new.txt");
    }

    @Test
    void unchangedHeadOnDirtyTreeStillIngests() throws Exception {
        Assumptions.assumeTrue(gitAvailable(), "git must be on PATH");

        run(repo, "git", "init");
        run(repo, "git", "config", "user.email", "test@test.local");
        run(repo, "git", "config", "user.name", "Test");
        Files.writeString(repo.resolve("a.txt"), "v1", StandardCharsets.UTF_8);
        run(repo, "git", "add", "a.txt");
        run(repo, "git", "commit", "-m", "first");

        String absolute = GuideProperties.resolvePath(
                repo.toAbsolutePath().normalize().toString(),
                System.getProperty("user.home"),
                System.getProperty("user.dir"));
        String head = GitIncrementalDirectorySupport.headCommit(repo).orElseThrow();
        Path stateFile = Files.createTempFile(repo.getParent(), "revisions", ".json");
        GitIngestionRevisionStore revisions = new GitIngestionRevisionStore(stateFile);
        revisions.putRevision(absolute, head);
        revisions.save();

        Files.writeString(repo.resolve("a.txt"), "dirty", StandardCharsets.UTF_8);
        assertThat(GitIncrementalDirectorySupport.headCommit(repo)).contains(head);

        Path references = Files.createTempFile(repo.getParent(), "references", ".yml");
        Files.writeString(references, "[]\n", StandardCharsets.UTF_8);

        ChunkingContentElementRepository store = mock(ChunkingContentElementRepository.class);
        HierarchicalContentReader reader = mock(HierarchicalContentReader.class);
        NavigableDocument document = mock(NavigableDocument.class);
        when(reader.parseFile(any(File.class), anyString())).thenReturn(document);

        GuideProperties properties = new GuideProperties(
                false,
                "default",
                null,
                absolute,
                null,
                references.toUri().toString(),
                new ContentConfig(
                        new VersionedContentConfig("https://example.invalid/", List.<String>of()),
                        List.<String>of()),
                "",
                List.of(absolute),
                Set.<String>of(),
                List.<FetchRoute>of(),
                new GuideProperties.GitIngestion(true, stateFile.toAbsolutePath().normalize().toString()),
                new GuideProperties.SpddProjection(false, ".", List.<String>of())
        );
        DataManager manager = new DataManager(store, properties, reader);

        IngestionResult result = manager.loadReferences();

        assertThat(GitIncrementalDirectorySupport.headCommit(repo)).contains(head);
        assertThat(result.failedDirectories()).isEmpty();
        assertThat(result.failedDocuments()).isEmpty();
        assertThat(result.ingestedDirectories()).contains(absolute);

        ArgumentCaptor<File> parsedFile = ArgumentCaptor.forClass(File.class);
        ArgumentCaptor<String> parsedName = ArgumentCaptor.forClass(String.class);
        verify(reader).parseFile(parsedFile.capture(), parsedName.capture());
        assertThat(parsedName.getValue()).isEqualTo("a.txt");
        assertThat(Files.readString(parsedFile.getValue().toPath())).isEqualTo("dirty");
        verify(store).writeAndChunkDocument(document);
    }

    @Test
    void findGitWorkTreeRootWalksUpFromSubdirectory() throws Exception {
        Assumptions.assumeTrue(gitAvailable(), "git must be on PATH");

        run(repo, "git", "init");
        Path nested = repo.resolve("spdd/canvas");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("readme.md"), "x", StandardCharsets.UTF_8);
        run(repo, "git", "config", "user.email", "test@test.local");
        run(repo, "git", "config", "user.name", "Test");
        run(repo, "git", "add", ".");
        run(repo, "git", "commit", "-m", "init");

        assertThat(GitIncrementalDirectorySupport.isGitWorkTree(nested)).isFalse();
        assertThat(GitIncrementalDirectorySupport.findGitWorkTreeRoot(nested))
                .contains(repo.toAbsolutePath().normalize());
    }

    @Test
    void filterPathsUnderDirectoryScopesRepoDiffToConfiguredSubdir() {
        Path root = Path.of("/repo").toAbsolutePath().normalize();
        Path canvas = root.resolve("spdd/canvas");
        List<String> changed = List.of(
                "spdd/canvas/FEAT-001.md",
                "spdd/analysis/notes.md",
                "README.md",
                "spdd/canvas/extra/nested.md"
        );
        assertThat(GitIncrementalDirectorySupport.filterPathsUnderDirectory(root, canvas, changed))
                .containsExactly("spdd/canvas/FEAT-001.md", "spdd/canvas/extra/nested.md");
        assertThat(GitIncrementalDirectorySupport.filterPathsUnderDirectory(root, root, changed))
                .containsExactlyElementsOf(changed);
    }

    private static boolean gitAvailable() throws Exception {
        Process p = new ProcessBuilder("git", "--version").start();
        return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
    }

    private static void run(Path cwd, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(p.waitFor(30, TimeUnit.SECONDS)).as(out).isTrue();
        assertThat(p.exitValue()).as(out).isZero();
    }
}
