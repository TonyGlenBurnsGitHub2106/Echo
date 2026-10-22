package com.echo;

import org.springframework.dao.DataAccessException;

/**
 * A save proc refused the write on purpose -- a double post, a date SQL Server cannot hold --
 * and wrote nothing. The message is the proc's own user-facing sentence and is shown as is
 * (GlobalExceptionHandler answers 409 with it).
 *
 * The contract with the proc: a refusal is a RAISERROR whose message ends in
 * "Nothing was saved." or "Nothing was added." -- or, for a refused delete or link,
 * "Nothing was deleted.", "Nothing was removed." or "Nothing was linked." (Story 5640; the
 * Gmail link procs already used the last one and were reaching the page as a bare 500) --
 * and is raised BEFORE any write. Every other SQL error stays a generic 500, exactly as
 * before -- no other SQL text reaches a browser.
 *
 * Born 2026-09-30: the Debt Resolution Creditors save posted four rows because the proc
 * inserted, then raised, and the page invited a retry.
 */
public class SaveRefusedException extends RuntimeException {

    public SaveRefusedException(String message) { super(message); }

    /** Rethrows a proc refusal as a SaveRefusedException; anything else is rethrown untouched. */
    public static RuntimeException translate(DataAccessException ex) {
        Throwable root = ex.getMostSpecificCause();
        String msg = root == null ? null : root.getMessage();
        if (msg != null) {
            String m = msg.trim();
            if (m.endsWith("Nothing was saved.") || m.endsWith("Nothing was added.")
                || m.endsWith("Nothing was deleted.") || m.endsWith("Nothing was removed.")
                || m.endsWith("Nothing was linked.")) {
                return new SaveRefusedException(m);
            }
        }
        return ex;
    }
}
