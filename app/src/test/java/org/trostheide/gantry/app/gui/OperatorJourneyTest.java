package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorJourneyTest {
    @Test
    void artworkComesBeforeConnectionAndIsExplicitlySafe() {
        OperatorJourney.Step step = step(false, false, false, false, false);

        assertEquals(OperatorJourney.State.EMPTY, step.state());
        assertEquals(OperatorJourney.Action.ADD_ARTWORK, step.action());
        assertEquals(OperatorJourney.Safety.SAFE, step.safety());
        assertTrue(step.message().contains("Nothing will move"));
    }

    @Test
    void artworkPreparationLeadsToConnectionThenSafetyCheck() {
        OperatorJourney.Step artwork = step(true, false, false, false, false);
        OperatorJourney.Step connected = step(true, true, false, false, false);

        assertEquals(OperatorJourney.Action.CONNECT, artwork.action());
        assertEquals(OperatorJourney.Safety.SAFE, artwork.safety());
        assertEquals(OperatorJourney.Action.CHECK_BEFORE_PLOTTING, connected.action());
        assertEquals(OperatorJourney.Safety.CONNECTED, connected.safety());
    }

    @Test
    void connectedEmptyStateNeverClaimsThatMovementIsImpossible() {
        OperatorJourney.Step step = step(false, true, false, false, false);

        assertEquals(OperatorJourney.Action.ADD_ARTWORK, step.action());
        assertEquals(OperatorJourney.Safety.CONNECTED, step.safety());
        assertTrue(step.message().contains("Manual movement"));
    }

    @Test
    void penChangeBecomesThePrimaryAction() {
        OperatorJourney.Step step = step(true, true, true, false, true);

        assertEquals(OperatorJourney.State.WAITING_FOR_PEN, step.state());
        assertEquals(OperatorJourney.Action.CONTINUE, step.action());
        assertEquals("Pen ready — continue", step.actionLabel());
        assertEquals(OperatorJourney.Safety.WAITING, step.safety());
    }

    @Test
    void activeAndPausedPlotsExposeTheSafeImmediateAction() {
        OperatorJourney.Step moving = step(true, true, true, false, false);
        OperatorJourney.Step paused = step(true, true, true, true, false);

        assertEquals(OperatorJourney.Action.PAUSE, moving.action());
        assertEquals(OperatorJourney.Safety.MOVING, moving.safety());
        assertEquals(OperatorJourney.Action.RESUME, paused.action());
        assertEquals(OperatorJourney.Safety.PAUSED, paused.safety());
    }

    private static OperatorJourney.Step step(boolean artwork, boolean connected,
            boolean plotting, boolean paused, boolean waiting) {
        return OperatorJourney.current(
                new OperatorJourney.Snapshot(artwork, connected, plotting, paused, waiting));
    }
}
