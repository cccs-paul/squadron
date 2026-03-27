package com.squadron.identity.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlugUtilsTest {

    @Test
    void should_generateSlug_when_simpleInput() {
        String result = SlugUtils.toSlug("Hello World");

        assertEquals("hello-world", result);
    }

    @Test
    void should_convertToLowercase_when_mixedCaseInput() {
        String result = SlugUtils.toSlug("Acme CORP");

        assertEquals("acme-corp", result);
    }

    @Test
    void should_replaceSpaces_when_multipleSpaces() {
        String result = SlugUtils.toSlug("hello   world");

        assertEquals("hello-world", result);
    }

    @Test
    void should_removeSpecialCharacters_when_inputContainsSpecialChars() {
        String result = SlugUtils.toSlug("Hello & World!");

        assertEquals("hello--world", result);
    }

    @Test
    void should_handleUnicode_when_accentedCharacters() {
        String result = SlugUtils.toSlug("cafe\u0301");

        assertEquals("cafe", result);
    }

    @Test
    void should_handleUnicode_when_umlautCharacters() {
        String result = SlugUtils.toSlug("M\u00fcnchen");

        assertEquals("munchen", result);
    }

    @Test
    void should_removeLeadingDashes_when_present() {
        String result = SlugUtils.toSlug("-hello");

        assertEquals("hello", result);
    }

    @Test
    void should_removeTrailingDashes_when_present() {
        String result = SlugUtils.toSlug("hello-");

        assertEquals("hello", result);
    }

    @Test
    void should_throwException_when_nullInput() {
        assertThrows(IllegalArgumentException.class, () -> SlugUtils.toSlug(null));
    }

    @Test
    void should_throwException_when_blankInput() {
        assertThrows(IllegalArgumentException.class, () -> SlugUtils.toSlug(""));
    }

    @Test
    void should_throwException_when_whitespaceOnlyInput() {
        assertThrows(IllegalArgumentException.class, () -> SlugUtils.toSlug("   "));
    }

    @Test
    void should_throwExceptionWithMessage_when_nullInput() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> SlugUtils.toSlug(null));

        assertEquals("Input for slug generation must not be blank", ex.getMessage());
    }

    @Test
    void should_preserveHyphens_when_alreadyPresent() {
        String result = SlugUtils.toSlug("my-slug");

        assertEquals("my-slug", result);
    }

    @Test
    void should_preserveNumbers_when_present() {
        String result = SlugUtils.toSlug("Project 2024");

        assertEquals("project-2024", result);
    }

    @Test
    void should_handleTabs_when_present() {
        String result = SlugUtils.toSlug("hello\tworld");

        assertEquals("hello-world", result);
    }

    @Test
    void should_preserveUnderscores_when_present() {
        String result = SlugUtils.toSlug("my_project");

        assertEquals("my_project", result);
    }

    @Test
    void should_handleMixedUnicodeAndAscii_when_combined() {
        String result = SlugUtils.toSlug("Caf\u00e9 Latt\u00e9");

        assertEquals("cafe-latte", result);
    }

    @Test
    void should_handleSingleCharacter_when_provided() {
        String result = SlugUtils.toSlug("A");

        assertEquals("a", result);
    }

    @Test
    void should_handleAlreadyLowercaseSlug_when_noChangesNeeded() {
        String result = SlugUtils.toSlug("already-a-slug");

        assertEquals("already-a-slug", result);
    }

    @Test
    void should_notBeInstantiable_when_utilityClass() throws NoSuchMethodException {
        var constructor = SlugUtils.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
    }

    @Test
    void should_beFinalClass_when_checked() {
        assertTrue(java.lang.reflect.Modifier.isFinal(SlugUtils.class.getModifiers()));
    }
}
