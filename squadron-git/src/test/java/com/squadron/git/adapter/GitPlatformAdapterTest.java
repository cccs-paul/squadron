package com.squadron.git.adapter;

import com.squadron.git.dto.CreatePullRequestRequest;
import com.squadron.git.dto.DiffResult;
import com.squadron.git.dto.PullRequestDto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GitPlatformAdapterTest {

    @Test
    void should_beInterface() {
        assertTrue(GitPlatformAdapter.class.isInterface());
    }

    @Test
    void should_defineGetPlatformTypeMethod() throws NoSuchMethodException {
        Method method = GitPlatformAdapter.class.getMethod("getPlatformType");
        assertEquals(String.class, method.getReturnType());
        assertEquals(0, method.getParameterCount());
    }

    @Test
    void should_defineCreatePullRequestMethod() throws NoSuchMethodException {
        Method method = GitPlatformAdapter.class.getMethod("createPullRequest", CreatePullRequestRequest.class);
        assertEquals(PullRequestDto.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_defineMergePullRequestMethod() throws NoSuchMethodException {
        Method method = GitPlatformAdapter.class.getMethod("mergePullRequest",
                String.class, String.class, String.class, String.class, String.class);
        assertEquals(void.class, method.getReturnType());
        assertEquals(5, method.getParameterCount());
    }

    @Test
    void should_defineAddReviewCommentMethod() throws NoSuchMethodException {
        Method method = GitPlatformAdapter.class.getMethod("addReviewComment",
                String.class, String.class, String.class, String.class, String.class);
        assertEquals(void.class, method.getReturnType());
        assertEquals(5, method.getParameterCount());
    }

    @Test
    void should_defineGetDiffMethod() throws NoSuchMethodException {
        Method method = GitPlatformAdapter.class.getMethod("getDiff",
                String.class, String.class, String.class, String.class);
        assertEquals(DiffResult.class, method.getReturnType());
        assertEquals(4, method.getParameterCount());
    }

    @Test
    void should_defineRequestReviewersMethod() throws NoSuchMethodException {
        Method method = GitPlatformAdapter.class.getMethod("requestReviewers",
                String.class, String.class, String.class, List.class, String.class);
        assertEquals(void.class, method.getReturnType());
        assertEquals(5, method.getParameterCount());
    }

    @Test
    void should_defineGetPullRequestMethod() throws NoSuchMethodException {
        Method method = GitPlatformAdapter.class.getMethod("getPullRequest",
                String.class, String.class, String.class, String.class);
        assertEquals(PullRequestDto.class, method.getReturnType());
        assertEquals(4, method.getParameterCount());
    }

    @Test
    void should_haveExactlySevenMethods() {
        Method[] methods = GitPlatformAdapter.class.getDeclaredMethods();
        assertEquals(7, methods.length);
    }
}
