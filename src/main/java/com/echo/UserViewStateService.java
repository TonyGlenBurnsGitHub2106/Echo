package com.echo;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Rules for remembered view state. Keys are the dotted names pages already used
 * in localStorage ('leo.cl.groups.16.except'); values are the same strings.
 * UserID follows {@link UserPreferencesService}: 1 while LeonardoWeb is single-user.
 */
@Service
public class UserViewStateService {

    static final int DEFAULT_USER_ID   = 1;
    static final int MAX_KEY_LENGTH    = 200;    // UserViewState.StateKey
    static final int MIN_PREFIX        = 3;      // never "read everything"
    static final int MAX_VALUE_LENGTH  = 4000;   // UserViewState.StateValue
    static final int MAX_KEYS_PER_SAVE = 50;

    private final UserViewStateRepository repo;

    public UserViewStateService(UserViewStateRepository repo) { this.repo = repo; }

    public Map<String, String> list(String prefix) {
        String p = prefix == null ? "" : prefix.trim();
        if (p.length() < MIN_PREFIX) throw new IllegalArgumentException("prefix must be at least " + MIN_PREFIX + " characters");
        if (p.length() > MAX_KEY_LENGTH) throw new IllegalArgumentException("prefix is longer than " + MAX_KEY_LENGTH + " characters");
        return repo.list(DEFAULT_USER_ID, p);
    }

    /** Validates every entry before writing any, so a bad batch writes nothing. */
    public int save(Map<String, Object> values) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("values is required");
        if (values.size() > MAX_KEYS_PER_SAVE) throw new IllegalArgumentException("at most " + MAX_KEYS_PER_SAVE + " keys per save");
        for (Map.Entry<String, Object> e : values.entrySet()) {
            String k = e.getKey() == null ? "" : e.getKey().trim();
            if (k.isEmpty()) throw new IllegalArgumentException("a key is blank");
            if (k.length() > MAX_KEY_LENGTH) throw new IllegalArgumentException("a key is longer than " + MAX_KEY_LENGTH + " characters");
            if (valueOf(e.getValue()).length() > MAX_VALUE_LENGTH) {
                throw new IllegalArgumentException("the value for " + k + " is longer than " + MAX_VALUE_LENGTH + " characters");
            }
        }
        for (Map.Entry<String, Object> e : values.entrySet()) {
            repo.save(DEFAULT_USER_ID, e.getKey().trim(), valueOf(e.getValue()));
        }
        return values.size();
    }

    private static String valueOf(Object raw) { return raw == null ? "" : raw.toString(); }
}
