package com.echo;

/**
 * Thrown when a save would violate a uniqueness constraint and the caller
 * should see a friendly validation message instead of a raw SQL error.
 */
public class DuplicateEntityException extends RuntimeException {
    public DuplicateEntityException(String message) { super(message); }
}
