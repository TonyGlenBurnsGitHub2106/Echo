package com.echo;

import com.echo.SolutionSearchModels.SearchFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The term rules of stp_Solution_Search, in Java, for the demo store (2026-10-16): a text
 * matches when ANY of the OR terms is in it (or there are none), EVERY AND term is in it and
 * NO NOT term is in it -- case-insensitive, a term anywhere inside a word counts. The snippet
 * is dbo.fn_Search_Snippet's: the width around the first positive term found in the body,
 * "..." where it was cut, the start of the body when no term is in it.
 */
final class SolutionSearchMatcher {

    static final int SNIPPET_WIDTH = 240;

    private final SearchFilter f;
    private final List<String> positive = new ArrayList<>();

    SolutionSearchMatcher(SearchFilter f) {
        this.f = f;
        positive.addAll(f.orTerms());
        positive.addAll(f.andTerms());
    }

    boolean matches(String text) {
        String hay = lower(text);
        if (!f.orTerms().isEmpty() && f.orTerms().stream().noneMatch(t -> hay.contains(lower(t)))) return false;
        if (f.andTerms().stream().anyMatch(t -> !hay.contains(lower(t)))) return false;
        return f.notTerms().stream().noneMatch(t -> hay.contains(lower(t)));
    }

    /** The first positive term found in the body, in the order typed, or null. */
    String matchTerm(String body) {
        String hay = lower(body);
        for (String t : positive) if (hay.contains(lower(t))) return t;
        return null;
    }

    static String snippet(String body, String term) {
        String text = body == null ? "" : body.replace("\r", " ").replace("\n", " ").replace("\t", " ");
        int width = SNIPPET_WIDTH;
        int pos = (term == null || term.isBlank()) ? 0 : lower(text).indexOf(lower(term)) + 1;   // 1-based, 0 = none, as CHARINDEX
        int start = pos > width / 3 ? pos - width / 3 : 1;
        String out = text.substring(Math.min(start - 1, text.length()), Math.min(start - 1 + width, text.length()));
        if (start > 1) out = "..." + out;
        if (start + width <= text.length()) out = out + "...";
        return out;
    }

    private static String lower(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }
}
