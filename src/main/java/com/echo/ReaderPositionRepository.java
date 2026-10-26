package com.echo;

/**
 * Repository for reading positions (dbo.ReaderPosition). Story 2422.
 */
public interface ReaderPositionRepository {

    /** Where the reader stopped in one document, or null if it was never read. */
    ReaderPosition find(String docKey);

    /** Record where the reader stopped, replacing whatever was there before. */
    ReaderPosition save(ReaderPosition draft);

    /** Forget one document's position, so it opens at the top again. */
    void delete(String docKey);
}
