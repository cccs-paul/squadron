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

    // ========================================================================
    // Security: Token sanitization tests
    // ========================================================================

    @Test
    void should_sanitizeOutput_removeOAuth2Token() {
        String input = "fatal: repository 'https://oauth2:ghp_abc123@github.com/owner/repo.git' not found";
        String sanitized = gitCliService.sanitizeOutput(input);
        assertFalse(sanitized.contains("ghp_abc123"), "Token should be scrubbed");
        assertTrue(sanitized.contains("***@"), "Token should be replaced with ***");
    }

    @Test
    void should_sanitizeOutput_removeUserPasswordCredentials() {
        String input = "fatal: unable to access 'https://user:secret-token@github.com/owner/repo.git'";
        String sanitized = gitCliService.sanitizeOutput(input);
        assertFalse(sanitized.contains("secret-token"), "Password should be scrubbed");
        assertTrue(sanitized.contains("***@"), "Credentials should be replaced with ***");
    }

    @Test
    void should_sanitizeOutput_preserveCleanOutput() {
        String input = "Enumerating objects: 5, done.\nCounting objects: 100% (5/5), done.";
        String sanitized = gitCliService.sanitizeOutput(input);
        assertEquals(input, sanitized);
    }

    @Test
    void should_sanitizeOutput_handleNull() {
        assertEquals("", gitCliService.sanitizeOutput(null));
    }

    @Test
    void should_sanitizeErrorOutput_inFailedGitCommands() throws IOException {
        String workDir = tempDir.resolve("clone-sanitize").toString();
        new File(workDir).mkdirs();

        // Clone with a fake token — will fail, but error output should be sanitized
        GitCommandResult result = gitCliService.clone("https://github.com/private/repo.git",
                null, workDir, "ghp_SECRET_TOKEN_VALUE");
        assertFalse(result.isSuccess());
        // Error output should have token scrubbed
        assertFalse(result.getErrorOutput().contains("ghp_SECRET_TOKEN_VALUE"),
                "Token should not appear in error output");
    }

    @Test
    void should_stripTokenFromRemoteUrl_afterPush() throws Exception {
        String workDir = tempDir.toString();
        initRepoWithCommit(workDir);

        // Add a remote
        new ProcessBuilder("git", "remote", "add", "origin", "https://github.com/owner/repo.git")
                .directory(new File(workDir)).start().waitFor();

        // Push with token (will fail because remote is fake, but verifies URL cleanup)
        gitCliService.push("origin", "main", workDir, "test-token");

        // Verify the remote URL has been stripped of the token
        ProcessBuilder pb = new ProcessBuilder("git", "remote", "get-url", "origin");
        pb.directory(new File(workDir));
        Process p = pb.start();
        String remoteUrl = new String(p.getInputStream().readAllBytes()).trim();
        p.waitFor();

        assertFalse(remoteUrl.contains("test-token"),
                "Token should have been stripped from remote URL after push, but got: " + remoteUrl);
        assertTrue(remoteUrl.startsWith("https://"),
                "Remote URL should still be a valid HTTPS URL: " + remoteUrl);
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
