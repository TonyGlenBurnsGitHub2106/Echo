package com.echo;

import java.util.List;

/**
 * Repository for notes written against reading marks (dbo.ReaderMarkNote).
 * Story 2411.
 */
public interface ReaderMarkNoteRepository {

    /** Every note on every mark in one document, in reading order. */
    List<ReaderMarkNote> listForDocument(String docKey);

    /** Write one note against one mark. Null when the mark no longer exists. */
    ReaderMarkNote add(int markId, String noteText);

    /** Rewrite one note. Null when the note no longer exists. */
    ReaderMarkNote update(int noteId, String noteText);

    /** Remove one note by id. The mark it belonged to is left alone. */
    void delete(int noteId);

    /** Remove every note on one mark, used when the mark itself is removed. */
    void deleteForMark(int markId);
}
