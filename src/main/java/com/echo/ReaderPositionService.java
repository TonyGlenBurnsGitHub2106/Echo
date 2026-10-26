package com.echo;

import org.springframework.stereotype.Service;

/**
 * Where the reader stopped reading (Story 2422). Kept apart from
 * {@link ReaderHighlightService} because a position is not a mark: it is one
 * row per document that is constantly overwritten as the reader scrolls, with
 * none of the marking surface's validation, colours, or research bookkeeping.
 *
 * <p>The rule that shapes everything here is that a position must never be
 * allowed to fail a read. A document whose position was never saved, or whose
 * saved place no longer makes sense, simply opens at the top -- so the service
 * normalizes rather than rejects, and answers a never-read document with null
 * rather than an error.
 */
@Service
public class ReaderPositionService {

    /** Longest document title we will keep alongside a position. */
    private static final int MAX_TITLE_LENGTH = 400;

    private final ReaderPositionRepository positions;

    public ReaderPositionService(ReaderPositionRepository positions) { this.positions = positions; }

    /** Raised when the caller named no document. Mapped to 400 by the controller. */
    public static class InvalidPositionException extends RuntimeException {
        public InvalidPositionException(String message) { super(message); }
    }

    /** Where the reader stopped, or null when this document was never read. */
    public ReaderPosition find(String docKey) {
        return positions.find(requireDocKey(docKey));
    }

    /** Record where the reader stopped, replacing whatever was there before. */
    public ReaderPosition save(ReaderPosition draft) {
        if (draft == null) throw new InvalidPositionException("A document is required.");
        draft.setDocKey(requireDocKey(draft.getDocKey()));
        draft.setDocTitle(clip(blankToNull(draft.getDocTitle()), MAX_TITLE_LENGTH));
        draft.setViewMode(isBlank(draft.getViewMode()) ? "reader" : draft.getViewMode().trim());
        if (draft.getBlockIndex() < 0) draft.setBlockIndex(0);
        if (draft.getCharOffset() < 0) draft.setCharOffset(0);
        if (draft.getPageNumber() != null && draft.getPageNumber().intValue() <= 0) draft.setPageNumber(null);
        draft.setScrollRatio(clampRatio(draft.getScrollRatio()));
        draft.setPercentRead(clampPercent(draft.getPercentRead()));
        return positions.save(draft);
    }

    /** Forget one document's position, so it opens at the top again. */
    public void forget(String docKey) {
        positions.delete(requireDocKey(docKey));
    }

    // ---- helpers ----------------------------------------------------------

    private static String requireDocKey(String docKey) {
        if (isBlank(docKey)) throw new InvalidPositionException("A document is required.");
        return docKey.trim();
    }

    /**
     * A ratio outside 0..1 is a rendering artifact (an over-scroll bounce on a
     * phone reports past the end), not something to refuse -- so it is pulled
     * back into range rather than failing the save.
     */
    private static double clampRatio(double v) {
        if (Double.isNaN(v) || v < 0) return 0;
        return v > 1 ? 1 : v;
    }

    private static int clampPercent(int v) { return v < 0 ? 0 : (v > 100 ? 100 : v); }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private static String blankToNull(String s) { return isBlank(s) ? null : s.trim(); }

    private static String clip(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }
}
