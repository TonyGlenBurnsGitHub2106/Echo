package com.echo;

/**
 * The caller is known and the record is theirs to see, but this action is not
 * theirs to take (403). A solution shared with an API client gives it FULL rights
 * inside. What stays the owner's is the solution's existence and boundary: creating
 * or deleting a solution, sharing it, choosing where its files are saved, managing
 * members, and printing (a page feature a client never reaches anyway).
 */
public class SolutionForbiddenException extends RuntimeException {
    public SolutionForbiddenException(String message) { super(message); }
}
