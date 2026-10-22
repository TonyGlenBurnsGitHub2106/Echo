package com.echo;

import java.util.Map;

/**
 * Remembered view state (UserViewState table) -- the open / collapsed / hidden
 * choices a LeonardoWeb page used to keep only in the browser's localStorage,
 * stored per user so the desktop browser and the phone PWA share them.
 *
 * Two implementations, chosen by {@code leonardo.demo.enabled}:
 *   - {@link JdbcUserViewStateRepository}     -- stp_UserViewState_List / stp_UserViewState_Save
 *   - {@link InMemoryUserViewStateRepository} -- a map, so tests never write the live table
 */
public interface UserViewStateRepository {

    /** Every key for this user that starts with {@code prefix}, matched literally, key -> value. */
    Map<String, String> list(int userId, String prefix);

    /** Upsert one key. An unchanged value is a no-op in the proc. */
    void save(int userId, String key, String value);
}
