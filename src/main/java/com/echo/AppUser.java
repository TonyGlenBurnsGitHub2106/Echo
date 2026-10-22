package com.echo;

import java.time.LocalDateTime;

/** One person who signs in to Echo (table AppUser, Story 5653). */
record AppUser(int id, String username, String displayName, String passwordHash, boolean active, boolean admin,
                int failedAttempts, LocalDateTime lockedUntil, int sessionGeneration) {

    boolean lockedNow() { return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now()); }
}
