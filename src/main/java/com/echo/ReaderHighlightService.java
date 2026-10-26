package com.echo;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Leonardo Reader's marking surface (Story 2307): saving, listing, and
 * removing highlights, plus the one flow that spans two shelves -- saving a
 * marked stretch of text to the Research shelf as an excerpt and remembering the
 * mark that produced it.
 *
 * <p>It also owns the notes written against a mark (Story 2411). Notes belong
 * to the marking surface rather than to a shelf of their own: they are read,
 * written, and thrown away inside the reader, alongside the mark they explain.
 *
 * <p>Excerpt creation is delegated to {@link ResearchService} so the Research
 * shelf keeps sole ownership of its own validation and normalization; this
 * service only adds the reader-side bookkeeping.
 */
@Service
public class ReaderHighlightService {

    /** Longest mark we will store; a "selection" past this is a mis-drag, not an excerpt. */
    private static final int MAX_TEXT_LENGTH = 20000;


    /** The colour a mark gets when the reader did not pick one. */
    public static final String DEFAULT_COLOR = "yellow";

    /**
     * The whole colour vocabulary, in the order the reader shows it. Kept here
     * rather than in the database so the palette is one list rather than a
     * lookup table nobody would ever edit; anything outside it falls back to
     * {@link #DEFAULT_COLOR} rather than being rejected, so a stale phone
     * cannot fail a save.
     */
    public static final Set<String> COLORS = new LinkedHashSet<>(
        Arrays.asList("yellow", "blue", "pink", "orange", "green", "purple"));

    /**
     * Codes that live in the same column but are NOT colours -- they classify
     * the mark. The Writing Filing Cabinet reader stores a bookmark as a
     * zero-length mark carrying "bookmark", so the code has to survive a save
     * untouched. Folding it into {@link #DEFAULT_COLOR} -- which is what the
     * palette did when it landed -- turns every new bookmark into an empty
     * yellow highlight the reader can neither see nor remove (Story 2395).
     *
     * <p>Reserved codes are accepted on SAVE only. Recolouring is a colour
     * operation, so {@link #setColor} still refuses to turn a highlight into a
     * bookmark.
     */
    public static final Set<String> RESERVED_CODES =
        new LinkedHashSet<>(Arrays.asList("bookmark"));

    /** Longest note we will store. Past this it is a paste accident, not a note. */
    private static final int MAX_NOTE_LENGTH = 20000;

    private final ReaderHighlightRepository highlights;
    private final ReaderMarkNoteRepository  notes;

    // Story 5653 (Echo): no ResearchService -- see saveExcerpt below.
    public ReaderHighlightService(ReaderHighlightRepository highlights,
                                  ReaderMarkNoteRepository notes) {
        this.highlights = highlights;
        this.notes      = notes;
    }

    /** Raised when the caller sent something unusable. Mapped to 400 by the controller. */
    public static class InvalidHighlightException extends RuntimeException {
        public InvalidHighlightException(String message) { super(message); }
    }


    public List<ReaderHighlight> listForDocument(String docKey) {
        return highlights.listForDocument(require(docKey, "A document is required."));
    }

    /** Save a plain highlight (no research link). */
    public ReaderHighlight save(ReaderHighlight draft) {
        return highlights.add(prepare(draft));
    }

    /** Recolour one saved mark. Returns the stored row, or null if it is gone. */
    public ReaderHighlight setColor(int id, String colorCode) {
        if (id <= 0) throw new InvalidHighlightException("That highlight no longer exists.");
        return highlights.setColor(id, normalizeColor(colorCode, false));
    }

    /**
     * Remove one mark, and the notes written against it. The notes go first:
     * a note pointing at a mark that is already gone is unreachable, whereas a
     * mark whose notes were cleared but which itself survived a failure is
     * merely a mark with nothing written on it.
     */
    public void delete(int id) {
        if (id <= 0) return;
        notes.deleteForMark(id);
        highlights.delete(id);
    }

    // ---- notes on a mark (Story 2411) -------------------------------------

    /** Every note on every mark in one document, in reading order. */
    public List<ReaderMarkNote> listNotesForDocument(String docKey) {
        return notes.listForDocument(require(docKey, "A document is required."));
    }

    /**
     * Write one note against one mark. Returns null when the mark is gone, so
     * the caller can say so rather than storing a note nothing can reach.
     */
    public ReaderMarkNote addNote(int markId, String noteText) {
        if (markId <= 0) throw new InvalidHighlightException("That mark no longer exists.");
        return notes.add(markId, requireNoteText(noteText));
    }

    /** Rewrite one note. Returns null when the note is gone. */
    public ReaderMarkNote updateNote(int noteId, String noteText) {
        if (noteId <= 0) throw new InvalidHighlightException("That note no longer exists.");
        return notes.update(noteId, requireNoteText(noteText));
    }

    /** Remove one note. The mark it was written against is left alone. */
    public void deleteNote(int noteId) {
        if (noteId > 0) notes.delete(noteId);
    }

    /**
     * Note text we are willing to store. Emptying a note is a delete, and the
     * caller has to say so -- otherwise a mis-tap silently wipes what was
     * written.
     */
    private static String requireNoteText(String noteText) {
        String text = require(noteText, "Write something first.");
        if (text.length() > MAX_NOTE_LENGTH) {
            throw new InvalidHighlightException("That note is too long to save.");
        }
        return text;
    }

    // Story 5653 (Echo): no saveExcerpt -- an excerpt is filed on Leonardo's Research shelf, which Echo
    // does not have. The Reader's Save Excerpt button is hidden in Echo (reader-echo.css).

    // ---- helpers ----------------------------------------------------------

    /** Normalizes and validates a draft mark before it is stored. */
    private ReaderHighlight prepare(ReaderHighlight h) {
        if (h == null) throw new InvalidHighlightException("Nothing to save.");
        h.setDocKey(require(h.getDocKey(), "A document is required."));
        h.setSelectedText(require(h.getSelectedText(), "Select some text first."));
        if (h.getSelectedText().length() > MAX_TEXT_LENGTH) {
            throw new InvalidHighlightException("That selection is too long to save.");
        }
        h.setDocTitle(blankToNull(h.getDocTitle()));
        h.setViewMode(isBlank(h.getViewMode()) ? "reader" : h.getViewMode().trim());
        h.setColorCode(normalizeColor(h.getColorCode(), true));
        if (h.getBlockIndex()  < 0) h.setBlockIndex(0);
        if (h.getStartOffset() < 0) h.setStartOffset(0);
        if (h.getEndOffset()   < h.getStartOffset()) h.setEndOffset(h.getStartOffset());
        if (h.getPageNumber() != null && h.getPageNumber().intValue() <= 0) h.setPageNumber(null);
        return h;
    }

    /**
     * A code we recognize, lower-cased; anything else becomes the default, so a
     * stale phone can never fail a save. With {@code allowReserved} set, the
     * non-colour marker codes ({@link #RESERVED_CODES}) pass through as well.
     */
    private static String normalizeColor(String colorCode, boolean allowReserved) {
        if (isBlank(colorCode)) return DEFAULT_COLOR;
        String wanted = colorCode.trim().toLowerCase();
        if (COLORS.contains(wanted)) return wanted;
        if (allowReserved && RESERVED_CODES.contains(wanted)) return wanted;
        return DEFAULT_COLOR;
    }

    private static String require(String value, String message) {
        if (isBlank(value)) throw new InvalidHighlightException(message);
        return value.trim();
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private static String blankToNull(String s) { return isBlank(s) ? null : s.trim(); }
}
