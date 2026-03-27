package com.squadron.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlugUtilsTest {

    @Test
    void should_convertToLowercase_when_inputHasUppercase() {
        assertEquals("hello-world", SlugUtils.toSlug("Hello World"));
    }

    @Test
    void should_replaceSpacesWithHyphens_when_inputHasSpaces() {
        assertEquals("my-team-name", SlugUtils.toSlug("My Team Name"));
    }

    @Test
    void should_removeSpecialCharacters_when_inputHasSpecialChars() {
        assertEquals("test-org", SlugUtils.toSlug("Test & Org!"));
    }

    @Test
    void should_collapseMultipleHyphens_when_inputProducesMultiple() {
        assertEquals("foo-bar", SlugUtils.toSlug("foo---bar"));
    }

    @Test
    void should_removeLeadingAndTrailingHyphens_when_present() {
        assertEquals("test", SlugUtils.toSlug("-test-"));
    }

    @Test
    void should_returnEmpty_when_inputIsNull() {
        assertEquals("", SlugUtils.toSlug(null));
    }

    @Test
    void should_returnEmpty_when_inputIsBlank() {
        assertEquals("", SlugUtils.toSlug("   "));
    }

    @Test
    void should_returnEmpty_when_inputIsEmpty() {
        assertEquals("", SlugUtils.toSlug(""));
    }

    @Test
    void should_handleAccentedCharacters_when_inputHasAccents() {
        String slug = SlugUtils.toSlug("Caf\u00e9 R\u00e9sum\u00e9");
        assertEquals("cafe-resume", slug);
    }

    @Test
    void should_handleNumbers_when_inputHasDigits() {
        assertEquals("team-42", SlugUtils.toSlug("Team 42"));
    }

    @Test
    void should_handleMultipleSpaces_when_inputHasConsecutiveSpaces() {
        assertEquals("foo-bar", SlugUtils.toSlug("foo   bar"));
    }

    @Test
    void should_handleAlreadyValidSlug_when_inputIsSlug() {
        assertEquals("already-a-slug", SlugUtils.toSlug("already-a-slug"));
    }
}
