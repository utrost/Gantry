package org.trostheide.gantry.app.gui;

/** Swing-free derivation of the operator's next action and current machine-safety state. */
final class OperatorJourney {
    enum State { EMPTY, ARTWORK_READY, CONNECTED, WAITING_FOR_PEN, MOVING, PAUSED }
    enum Action { ADD_ARTWORK, CONNECT, CHECK_BEFORE_PLOTTING, CONTINUE, PAUSE, RESUME }
    enum Safety {
        SAFE("Safe — nothing will move"),
        CONNECTED("Connected — manual movement enabled"),
        WAITING("Waiting for pen — machine paused"),
        MOVING("Plotter moving"),
        PAUSED("Paused — machine will stay still");

        private final String label;
        Safety(String label) { this.label = label; }
        String label() { return label; }
    }

    record Snapshot(boolean hasArtwork, boolean connected, boolean plotting,
                    boolean paused, boolean waitingForPen) { }
    record Step(State state, String message, String actionLabel, Action action, Safety safety) { }

    private OperatorJourney() { }

    static Step current(Snapshot snapshot) {
        if (snapshot.plotting() && snapshot.waitingForPen()) {
            return new Step(State.WAITING_FOR_PEN,
                    "Fit the requested pen or colour. The machine is waiting for you.",
                    "Pen ready — continue", Action.CONTINUE, Safety.WAITING);
        }
        if (snapshot.plotting() && snapshot.paused()) {
            return new Step(State.PAUSED,
                    "Plotting is paused. The machine will stay still until you resume.",
                    "Resume plotting", Action.RESUME, Safety.PAUSED);
        }
        if (snapshot.plotting()) {
            return new Step(State.MOVING,
                    "The plotter is moving. Pause if you need to interrupt the job safely.",
                    "Pause", Action.PAUSE, Safety.MOVING);
        }
        if (!snapshot.hasArtwork()) {
            String message = snapshot.connected()
                    ? "Add the drawing you want Gantry to reproduce. Manual movement is available while connected."
                    : "Add the drawing you want Gantry to reproduce. Nothing will move yet.";
            return new Step(State.EMPTY, message, "Add artwork", Action.ADD_ARTWORK,
                    snapshot.connected() ? Safety.CONNECTED : Safety.SAFE);
        }
        if (!snapshot.connected()) {
            return new Step(State.ARTWORK_READY,
                    "Check the artwork's size and position, then connect when you are ready.",
                    "Connect plotter", Action.CONNECT, Safety.SAFE);
        }
        return new Step(State.CONNECTED,
                "The plotter is connected. Run the safety check before plotting.",
                "Check before plotting", Action.CHECK_BEFORE_PLOTTING, Safety.CONNECTED);
    }
}
