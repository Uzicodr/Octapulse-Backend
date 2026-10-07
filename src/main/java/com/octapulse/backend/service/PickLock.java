package com.octapulse.backend.service;

import com.octapulse.backend.domain.Fight;

import java.time.Instant;

/**
 * A fight locks at its explicit locked_at, else its own start, else the event start.
 * A fight with a winner is always locked. Keep in sync with {@link #SQL_LOCKED}.
 */
public final class PickLock {

    /** SQL predicate equivalent to {@link #isLocked}; expects aliases f (fights) and e (events). */
    public static final String SQL_LOCKED =
            "(COALESCE(f.locked_at, f.starts_at, e.starts_at) <= now() OR f.winner_fighter_id IS NOT NULL)";

    private PickLock() {}

    public static boolean isLocked(Fight fight) {
        if (fight.getWinnerFighterId() != null) {
            return true;
        }
        Instant lockAt = fight.getLockedAt();
        if (lockAt == null) {
            lockAt = fight.getStartsAt() != null ? fight.getStartsAt() : fight.getEvent().getStartsAt();
        }
        return lockAt != null && !lockAt.isAfter(Instant.now());
    }
}
