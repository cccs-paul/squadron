package com.squadron.common.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

public final class SlugUtils {

    private SlugUtils() {
        // Prevent instantiation
    }

    private static final Pattern NON_ALPHANUMERIC_HYPHEN = Pattern.compile("[^a-z0-9-]");
    private static final Pattern MULTIPLE_HYPHENS = Pattern.compile("-{2,}");
    private static final Pattern LEADING_TRAILING_HYPHENS = Pattern.compile("^-|-$");

    public static String toSlug(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String slug = normalized.toLowerCase()
                .replaceAll("\\s+", "-");
        slug = NON_ALPHANUMERIC_HYPHEN.matcher(slug).replaceAll("");
        slug = MULTIPLE_HYPHENS.matcher(slug).replaceAll("-");
        slug = LEADING_TRAILING_HYPHENS.matcher(slug).replaceAll("");

        return slug;
    }
}
