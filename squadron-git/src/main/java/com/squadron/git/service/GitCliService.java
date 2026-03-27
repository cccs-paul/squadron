package com.squadron.git.service;

import com.squadron.git.dto.GitCommandResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service for executing git CLI commands via ProcessBuilder.
 * All operations target a workspace directory.
 */
@Service
public class GitCliService {

    private static final Logger log = LoggerFactory.getLogger(GitCliService.class);
    private static final long COMMAND_TIMEOUT_SECONDS = 300;

    /**
     * Clone a repository into the specified working directory.
     */
    public GitCommandResult clone(String repoUrl, String branch, String workDir, String accessToken) {
        log.info("Cloning repository {} into {}", repoUrl, workDir);
        String authenticatedUrl = injectTokenIntoUrl(repoUrl, accessToken);

        List<String> args = new ArrayList<>();
        args.add("clone");
        if (branch != null && !branch.isBlank()) {
            args.add("--branch");
            args.add(branch);
        }
        args.add(authenticatedUrl);
        args.add(".");

        return executeGitCommand(workDir, args.toArray(new String[0]));
    }

    /**
     * Checkout an existing branch.
     */
    public GitCommandResult checkout(String branch, String workDir) {
        log.info("Checking out branch {} in {}", branch, workDir);
        return executeGitCommand(workDir, "checkout", branch);
    }

    /**
     * Create and checkout a new branch.
     */
    public GitCommandResult createBranch(String branchName, String workDir) {
        log.info("Creating branch {} in {}", branchName, workDir);
        return executeGitCommand(workDir, "checkout", "-b", branchName);
    }

    /**
     * Stage all changes and commit.
     */
    public GitCommandResult commit(String message, String authorName, String authorEmail, String workDir) {
        log.info("Committing changes in {} with message: {}", workDir, message);

        // Stage all changes first
        GitCommandResult addResult = executeGitCommand(workDir, "add", "-A");
        if (!addResult.isSuccess()) {
            return addResult;
        }

        List<String> args = new ArrayList<>();
        args.add("commit");
        args.add("-m");
        args.add(message);
        if (authorName != null && authorEmail != null) {
            args.add("--author");
            args.add(authorName + " <" + authorEmail + ">");
        }

        return executeGitCommand(workDir, args.toArray(new String[0]));
    }

    /**
     * Push to a remote.
     */
    public GitCommandResult push(String remote, String branch, String workDir, String accessToken) {
        log.info("Pushing to {}/{} from {}", remote, branch, workDir);

        // If an access token is provided, set the remote URL with credentials
        if (accessToken != null && !accessToken.isBlank()) {
            GitCommandResult urlResult = executeGitCommand(workDir, "remote", "get-url", remote);
            if (urlResult.isSuccess() && urlResult.getOutput() != null) {
                String originalUrl = urlResult.getOutput().trim();
                String authenticatedUrl = injectTokenIntoUrl(originalUrl, accessToken);
                executeGitCommand(workDir, "remote", "set-url", remote, authenticatedUrl);
            }
        }

        List<String> args = new ArrayList<>();
        args.add("push");
        args.add(remote);
        if (branch != null && !branch.isBlank()) {
            args.add(branch);
        }
        args.add("--set-upstream");

        return executeGitCommand(workDir, args.toArray(new String[0]));
    }

    /**
     * Get the diff against a base branch.
     */
    public GitCommandResult diff(String baseBranch, String workDir) {
        log.info("Getting diff against {} in {}", baseBranch, workDir);
        return executeGitCommand(workDir, "diff", baseBranch);
    }

    /**
     * Execute a git command in the specified working directory.
     */
    private GitCommandResult executeGitCommand(String workDir, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));

        // Sanitize command for logging (hide tokens)
        String sanitizedCommand = command.stream()
                .map(arg -> arg.contains("@") && arg.contains("://") ? "[REDACTED_URL]" : arg)
                .collect(Collectors.joining(" "));
        log.debug("Executing: {}", sanitizedCommand);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(new File(workDir));
            processBuilder.environment().put("GIT_TERMINAL_PROMPT", "0");
            processBuilder.redirectErrorStream(false);

            Process process = processBuilder.start();

            String output;
            String errorOutput;
            try (BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                output = stdoutReader.lines().collect(Collectors.joining("\n"));
                errorOutput = stderrReader.lines().collect(Collectors.joining("\n"));
            }

            boolean completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return GitCommandResult.builder()
                        .success(false)
                        .output(output)
                        .errorOutput("Command timed out after " + COMMAND_TIMEOUT_SECONDS + " seconds")
                        .exitCode(-1)
                        .build();
            }

            int exitCode = process.exitValue();
            boolean success = exitCode == 0;

            if (!success) {
                log.warn("Git command failed (exit {}): {} - {}", exitCode, sanitizedCommand, errorOutput);
            }

            return GitCommandResult.builder()
                    .success(success)
                    .output(output)
                    .errorOutput(errorOutput)
                    .exitCode(exitCode)
                    .build();
        } catch (Exception e) {
            log.error("Failed to execute git command: {}", sanitizedCommand, e);
            return GitCommandResult.builder()
                    .success(false)
                    .output("")
                    .errorOutput(e.getMessage())
                    .exitCode(-1)
                    .build();
        }
    }

    /**
     * Inject an access token into an HTTPS URL for authentication.
     * Transforms https://github.com/owner/repo.git to https://token@github.com/owner/repo.git
     */
    private String injectTokenIntoUrl(String repoUrl, String accessToken) {
        if (accessToken == null || accessToken.isBlank() || repoUrl == null) {
            return repoUrl;
        }
        try {
            URI uri = URI.create(repoUrl);
            if ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) {
                return uri.getScheme() + "://oauth2:" + accessToken + "@" + uri.getHost()
                        + (uri.getPort() > 0 ? ":" + uri.getPort() : "")
                        + uri.getPath();
            }
        } catch (Exception e) {
            log.warn("Could not inject token into URL: {}", e.getMessage());
        }
        return repoUrl;
    }
}
