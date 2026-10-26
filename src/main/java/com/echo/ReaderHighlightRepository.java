package com.echo;

import java.util.List;

/**
 * Repository for saved Leonardo Reader highlights (dbo.ReaderHighlight).
 * Story 2307.
 */
public interface ReaderHighlightRepository {

    /** Every saved highlight for one document, in reading order. */
    List<ReaderHighlight> listForDocument(String docKey);

    /** Save one highlight and return the stored row. */
    ReaderHighlight add(ReaderHighlight draft);

    /** Change one highlight's colour and return the stored row. */
    ReaderHighlight setColor(int id, String colorCode);

    /** Remove one highlight by id. */
    void delete(int id);
}
