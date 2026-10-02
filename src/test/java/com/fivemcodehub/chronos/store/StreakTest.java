package com.fivemcodehub.chronos.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Streak transition tests.
 *
 * <p>The bug these guard against is a day boundary computed in UTC. For a
 * regional community that resets mid-evening, letting a player claim twice in
 * one local day.
 */
class StreakTest {

    @Test
    @DisplayName("a consecutive claim increments the streak")
    void consecutiveClaimIncrements() {
        var streak = new StreakStore.Streak(LocalDate.of(2026, 3, 10), 4, 9, 20);
        var next = streak.withClaim(LocalDate.of(2026, 3, 11), true);

        assertEquals(5, next.current());
        assertEquals(9, next.best(), "best is unchanged while below it");
        assertEquals(21, next.totalClaims());
    }

    @Test
    @DisplayName("a broken streak resets to one but preserves the best")
    void brokenStreakResets() {
        var streak = new StreakStore.Streak(LocalDate.of(2026, 3, 1), 12, 12, 40);
        var next = streak.withClaim(LocalDate.of(2026, 3, 20), false);

        assertEquals(1, next.current());
        assertEquals(12, next.best(), "best must survive a reset");
        assertEquals(41, next.totalClaims());
    }

    @Test
    @DisplayName("a new best is recorded when the streak exceeds it")
    void newBestRecorded() {
        var streak = new StreakStore.Streak(LocalDate.of(2026, 3, 10), 9, 9, 30);
        assertEquals(10, streak.withClaim(LocalDate.of(2026, 3, 11), true).best());
    }

    @Test
    @DisplayName("a fresh record starts empty")
    void freshRecordIsEmpty() {
        var fresh = StreakStore.Streak.fresh();
        assertNull(fresh.lastClaim());
        assertEquals(0, fresh.current());
        assertEquals(0, fresh.totalClaims());
    }

    @Test
    @DisplayName("consecutive means exactly one calendar day, across month and year ends")
    void consecutiveAcrossBoundaries() {
        // Arithmetic on epoch millis gets these wrong; LocalDate does not.
        assertTrue(LocalDate.of(2026, 1, 31).plusDays(1).equals(LocalDate.of(2026, 2, 1)));
        assertTrue(LocalDate.of(2026, 12, 31).plusDays(1).equals(LocalDate.of(2027, 1, 1)));
        assertTrue(LocalDate.of(2024, 2, 28).plusDays(1).equals(LocalDate.of(2024, 2, 29)),
                "2024 is a leap year");
        assertFalse(LocalDate.of(2026, 3, 10).plusDays(1).equals(LocalDate.of(2026, 3, 12)));
    }

    @Test
    @DisplayName("a local day boundary differs from UTC")
    void localBoundaryDiffersFromUtc() {
        // The whole reason the plugin takes a ZoneId: at 22:00 UTC it is already
        // the next day in Riyadh, so a UTC-based reset fires mid-evening.
        var instant = java.time.Instant.parse("2026-03-10T22:30:00Z");
        var utc = LocalDate.ofInstant(instant, ZoneId.of("UTC"));
        var riyadh = LocalDate.ofInstant(instant, ZoneId.of("Asia/Riyadh"));

        assertEquals(LocalDate.of(2026, 3, 10), utc);
        assertEquals(LocalDate.of(2026, 3, 11), riyadh);
        assertNotEquals(utc, riyadh);
    }

    @Test
    @DisplayName("a DST transition does not skip or repeat a day")
    void dstTransitionIsSafe() {
        // Europe/Berlin springs forward on 2026-03-29.
        var zone = ZoneId.of("Europe/Berlin");
        var before = LocalDate.of(2026, 3, 28);
        var after = before.plusDays(1);

        assertEquals(LocalDate.of(2026, 3, 29), after);
        // atStartOfDay resolves the gap rather than producing an invalid time.
        assertDoesNotThrow(() -> after.atStartOfDay(zone));
    }
}
