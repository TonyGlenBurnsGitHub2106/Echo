package com.echo;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mail Triage (Story 3561): turns a From header into the key a rule is kept under.
 *
 * Most senders are keyed by their REGISTRABLE domain, so ss.email.nextdoor.com and
 * is.email.nextdoor.com are both nextdoor.com and one decision covers both. A free-mail provider
 * (gmail.com, yahoo.com, ...) is shared by millions of people, so blacklisting the domain would
 * throw away every friend's mail -- those senders are keyed by their full address instead.
 */
public final class MailTriageSenders {

    private MailTriageSenders() {}

    public static final String DOMAIN  = "domain";
    public static final String ADDRESS = "address";

    /** Providers where the domain says nothing about the sender. */
    static final Set<String> FREE_MAIL = Set.of(
        "gmail.com", "googlemail.com", "yahoo.com", "ymail.com", "rocketmail.com",
        "hotmail.com", "outlook.com", "live.com", "msn.com", "aol.com",
        "icloud.com", "me.com", "mac.com", "comcast.net", "att.net", "sbcglobal.net",
        "bellsouth.net", "verizon.net", "cox.net", "charter.net", "earthlink.net",
        "protonmail.com", "proton.me", "gmx.com", "mail.com", "zoho.com", "yandex.com");

    /** Second-level labels used under a two-letter country code: example.co.uk, example.com.au. */
    private static final Set<String> COUNTRY_SECOND_LEVEL = Set.of(
        "co", "com", "net", "org", "gov", "ac", "edu", "ne", "or", "go");

    private static final Pattern ANGLE = Pattern.compile("<([^<>\\s]+@[^<>\\s]+)>");
    private static final Pattern BARE  = Pattern.compile("([A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})");

    /** A rule key and what kind it is. */
    public record Key(String key, String kind) {}

    /** The email address inside a From header ("Nextdoor &lt;reply@ss.email.nextdoor.com&gt;"), or null. */
    public static String address(String from) {
        if (from == null) return null;
        Matcher m = ANGLE.matcher(from);
        String a = m.find() ? m.group(1) : null;
        if (a == null) {
            Matcher b = BARE.matcher(from);
            a = b.find() ? b.group(1) : null;
        }
        return a == null ? null : a.trim().toLowerCase(Locale.ROOT);
    }

    /** The registrable domain of a host: ss.email.nextdoor.com -> nextdoor.com. */
    public static String registrableDomain(String host) {
        if (host == null) return null;
        String h = host.trim().toLowerCase(Locale.ROOT);
        while (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        String[] labels = h.split("\\.");
        if (labels.length < 2) return null;
        int keep = 2;
        String tld = labels[labels.length - 1];
        String second = labels[labels.length - 2];
        if (labels.length >= 3 && tld.length() == 2 && COUNTRY_SECOND_LEVEL.contains(second)) keep = 3;
        StringBuilder sb = new StringBuilder();
        for (int i = labels.length - keep; i < labels.length; i++) {
            if (sb.length() > 0) sb.append('.');
            sb.append(labels[i]);
        }
        return sb.toString();
    }

    /** The rule key for a From header, or null when it holds no usable address. */
    public static Key keyFor(String from) {
        String a = address(from);
        if (a == null) return null;
        String domain = registrableDomain(a.substring(a.indexOf('@') + 1));
        if (domain == null) return null;
        return FREE_MAIL.contains(domain) ? new Key(a, ADDRESS) : new Key(domain, DOMAIN);
    }

    /**
     * A sender Tony typed by hand: an address keeps its address only for free mail; any other
     * address, or a host, becomes its registrable domain. Returns null when it is not a sender.
     */
    public static Key normalizeTyped(String typed) {
        if (typed == null) return null;
        String t = typed.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("@")) t = t.substring(1);
        if (t.isEmpty() || t.contains(" ")) return null;
        if (t.contains("@")) return keyFor(t);
        String domain = registrableDomain(t);
        if (domain == null || !domain.matches("[a-z0-9.-]+\\.[a-z]{2,}")) return null;
        return FREE_MAIL.contains(domain) ? null : new Key(domain, DOMAIN);
    }
}
