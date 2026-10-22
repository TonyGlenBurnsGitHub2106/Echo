package com.echo;

/**
 * A rule of the Solutions family refused the request -- a duplicate name, an
 * item moved under its own sub-item, a project from another solution. The
 * message is written for the reader and reaches the caller verbatim (409). On
 * the real database these are the RAISERROR texts of the stp_Solution* and
 * stp_ApiClient* procs; the demo repositories throw the same sentences.
 */
public class SolutionRuleException extends RuntimeException {
    public SolutionRuleException(String message) { super(message); }
}
