package com.squadron.git.service;

import com.squadron.git.dto.GitCommandResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GitCliServiceTest {

    private GitCliService gitCliService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        gitCliService = new GitCliService();
    }

    @Test
    void should_initAndCommit_inLocalRepo() throws IOException {
        String workDir = tempDir.toString();

        // Initialize a git repo
        GitCommandResult initResult = executeGitInit(workDir);
        assertTrue(initResult.isSuccess(), "git init should succeed: " + initResult.getErrorOutput());

        // Create a file
        Files.writeString(tempDir.resolve("test.txt"), "hello world");

        // Commit
        GitCommandResult commitResult = gitCliService.commit("Initial commit", "Test User", "test@test.com", workDir);
        assertTrue(commitResult.isSuccess(), "git commit should succeed: " + commitResult.getErrorOutput());
    }

    @Test
    void should_createBranch_successfully() throws IOException {
        String workDir = tempDir.toString();
        initRepoWithCommit(workDir);

        GitCommandResult result = gitCliService.createBranch("feature/test", workDir);
        assertTrue(result.isSuccess(), "git checkout -b should succeed: " + result.getErrorOutput());
    }

    @Test
    void should_checkout_existingBranch() throws IOException {
        String workDir = tempDir.toString();
        initRepoWithCommit(workDir);

        // Create a branch and switch back
        gitCliService.createBranch("feature/test", workDir);
        GitCommandResult checkoutMain = gitCliService.checkout("master", workDir);
        // May be "master" or "main" depending on git config
        if (!checkoutMain.isSuccess()) {
            checkoutMain = gitCliService.checkout("main", workDir);
        }
        // Now checkout the feature branch
        GitCommandResult result = gitCliService.checkout("feature/test", workDir);
        assertTrue(result.isSuccess(), "git checkout should succeed: " + result.getErrorOutput());
    }

    @Test
    void should_diff_againstBranch() throws IOException {
        String workDir = tempDir.toString();
        initRepoWithCommit(workDir);

        // Create a new branch and make changes
        gitCliService.createBranch("feature/diff-test", workDir);
        Files.writeString(tempDir.resolve("new-file.txt"), "new content");

        GitCommandResult result = gitCliService.diff("HEAD", workDir);
        // Diff against HEAD shows no staged changes (files are untracked)
        assertTrue(result.isSuccess(), "git diff should succeed: " + result.getErrorOutput());
    }

    @Test
    void should_failGracefully_onInvalidWorkDir() {
        String invalidDir = "/nonexistent/path/dir";
        GitCommandResult result = gitCliService.checkout("main", invalidDir);
        assertFalse(result.isSuccess());
    }

    @Test
    void should_failCommit_whenNothingToCommit() throws IOException {
        String workDir = tempDir.toString();
        initRepoWithCommit(workDir);

        // Try to commit with no changes
        GitCommandResult result = gitCliService.commit("Empty commit", "Test", "test@test.com", workDir);
        assertFalse(result.isSuccess(), "Commit with no changes should fail");
    }

    @Test
    void should_clone_failsWithInvalidUrl() {
        String workDir = tempDir.resolve("clone-target").toString();
        new File(workDir).mkdirs();

        GitCommandResult result = gitCliService.clone("https://invalid.example.com/nonexistent/repo.git",
                null, workDir, null);
        assertFalse(result.isSuccess());
    }

    @Test
    void should_push_failsWithNoRemote() throws IOException {
        String workDir = tempDir.toString();
        initRepoWithCommit(workDir);

        GitCommandResult result = gitCliService.push("origin", "main", workDir, null);
        assertFalse(result.isSuccess(), "Push with no remote should fail");
    }

    @Test
    void should_clone_withBranch() {
        String workDir = tempDir.resolve("clone-branch").toString();
        new File(workDir).mkdirs();

        // This will fail because the URL is invalid, but it tests the branch parameter path
        GitCommandResult result = gitCliService.clone("https://invalid.example.com/repo.git",
                "develop", workDir, null);
        assertFalse(result.isSuccess());
    }

    @Test
    void should_clone_withAccessToken() {
        String workDir = tempDir.resolve("clone-token").toString();
        new File(workDir).mkdirs();

        // Tests the token injection path
        GitCommandResult result = gitCliService.clone("https://github.com/private/repo.git",
                null, workDir, "test-token");
        assertFalse(result.isSuccess()); // Will fail due to invalid token, but tests the path
    }

    private void initRepoWithCommit(String workDir) throws IOException {
        executeGitInit(workDir);
        Files.writeString(Path.of(workDir, "README.md"), "# Test");
        gitCliService.commit("Initial commit", "Test User", "test@test.com", workDir);
    }

    private GitCommandResult executeGitInit(String workDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "init");
            pb.directory(new File(workDir));
            pb.environment().put("GIT_TERMINAL_PROMPT", "0");
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                // Configure local git identity so commits work without global config
                executeGitConfig(workDir, "user.name", "Test User");
                executeGitConfig(workDir, "user.email", "test@test.com");
            }

            return GitCommandResult.builder()
                    .success(exitCode == 0)
                    .output("")
                    .errorOutput("")
                    .exitCode(exitCode)
                    .build();
        } catch (Exception e) {
            return GitCommandResult.builder()
                    .success(false)
                    .output("")
                    .errorOutput(e.getMessage())
                    .exitCode(-1)
                    .build();
        }
    }

    private void executeGitConfig(String workDir, String key, String value) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("git", "config", key, value);
        pb.directory(new File(workDir));
        pb.start().waitFor();
    }
}
