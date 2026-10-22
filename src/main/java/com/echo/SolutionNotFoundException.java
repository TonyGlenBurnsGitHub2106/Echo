package com.echo;

/**
 * The record does not exist -- or the caller is an API client that may not see
 * it. Both answer 404 on purpose: a client is never told that something it
 * cannot reach is there.
 */
public class SolutionNotFoundException extends RuntimeException {
    public SolutionNotFoundException(String what) { super(what + " not found."); }
}
