package org.trostheide.gantry.watercolor;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;
import org.trostheide.gantry.model.command.RefillCommand;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LayerRefillPlannerTest {
    @Test void assignsStationAndSplitsAtConfiguredDistance() {
        Layer input = new Layer("ink", "old", "#123456", 100, List.of(
                new RefillCommand(1, "old"), new MoveCommand(2, 0, 0),
                new DrawCommand(3, List.of(new Point(0, 0), new Point(25, 0)))));

        Layer result = LayerRefillPlanner.configure(input, "blue-pot", 10);

        assertEquals("blue-pot", result.stationId());
        assertEquals(10, result.maxDrawDistance());
        assertEquals(3, result.commands().stream().filter(RefillCommand.class::isInstance).count());
        assertTrue(result.commands().stream().filter(RefillCommand.class::isInstance)
                .map(RefillCommand.class::cast).allMatch(r -> r.stationId.equals("blue-pot")));
    }

    @Test void zeroDistanceRemovesAutomaticRefills() {
        Layer input = new Layer("ink", "old", null, 10, List.of(
                new RefillCommand(1, "old"), new DrawCommand(2,
                List.of(new Point(0, 0), new Point(5, 0)))));
        Layer result = LayerRefillPlanner.configure(input, "new", 0);
        assertEquals(0, result.commands().stream().filter(RefillCommand.class::isInstance).count());
    }

    @Test void countsMoveToSingleDrawEndpointAndRestoresTheArtworkStart() {
        Layer input = new Layer("ink", "pot", null, 0, List.of(
                new MoveCommand(1, 0, 0), new DrawCommand(2, List.of(new Point(25, 0)))));
        Layer result = LayerRefillPlanner.configure(input, "pot", 10);
        assertEquals(3, result.commands().stream().filter(RefillCommand.class::isInstance).count());
        assertPaintedPath(result, 25, 10);
    }

    @Test void exactBoundaryFlushesPendingDrawingBeforeRefilling() {
        Layer input = new Layer("ink", "pot", null, 0, List.of(
                new MoveCommand(1, 0, 0),
                new DrawCommand(2, List.of(new Point(0, 0), new Point(8, 0))),
                new DrawCommand(3, List.of(new Point(8, 0), new Point(10, 0), new Point(15, 0)))));
        assertPaintedPath(LayerRefillPlanner.configure(input, "pot", 10), 15, 10);
    }

    @Test void drawOnlyLayerReturnsFromInitialRefillToItsAssumedOrigin() {
        Layer input = new Layer("ink", "pot", null, 0,
                List.of(new DrawCommand(1, List.of(new Point(5, 0)))));
        Layer result = LayerRefillPlanner.configure(input, "pot", 10);
        assertInstanceOf(RefillCommand.class, result.commands().get(0));
        MoveCommand start = assertInstanceOf(MoveCommand.class, result.commands().get(1));
        assertEquals(0, start.x); assertEquals(0, start.y);
        assertPaintedPath(result, 5, 10);
    }


    @Test void repeatedPlanningPreservesDiagonalInkAndPenUpGapsAcrossRefillBudgets() {
        Layer input = new Layer("ink", "pot", null, 0, List.of(
                new MoveCommand(1, 10, 5), new DrawCommand(2, List.of(new Point(14, 8))),
                new DrawCommand(3, List.of(new Point(14, 13))),
                new MoveCommand(4, 40, 10), new DrawCommand(5, List.of(new Point(43, 14)))));
        for (double budget : new double[] {0.5, 1, 4, 5, 7, 10, 20}) {
            Layer result = LayerRefillPlanner.configure(input, "pot", budget);
            result = LayerRefillPlanner.configure(result, "pot", budget); // editing settings twice
            Point current = null;
            double total = 0, sinceRefill = 0;
            java.util.Set<Integer> ids = new java.util.HashSet<>();
            for (var command : result.commands()) {
                assertTrue(ids.add(command.getId()), "Command IDs must stay unique");
                if (command instanceof RefillCommand) { current=null; sinceRefill=0; }
                else if (command instanceof MoveCommand move) current=new Point(move.x, move.y);
                else if (command instanceof DrawCommand draw) for (Point point : draw.points) {
                    assertNotNull(current, "A refill must return to artwork before drawing");
                    double length=Math.hypot(point.x()-current.x(), point.y()-current.y());
                    total+=length; sinceRefill+=length;
                    assertTrue(sinceRefill<=budget+1e-8);
                    if(length>1e-8) {
                        boolean first = current.x()>=10 && point.x()<=14 && point.x()>=current.x()
                                && Math.abs((current.y()-5)*4-(current.x()-10)*3)<1e-8
                                && Math.abs((point.y()-5)*4-(point.x()-10)*3)<1e-8;
                        boolean second = Math.abs(current.x()-14)<1e-8 && Math.abs(point.x()-14)<1e-8
                                && current.y()>=8-1e-8 && point.y()<=13+1e-8 && point.y()>=current.y();
                        boolean third = current.x()>=40 && point.x()<=43 && point.x()>=current.x()
                                && Math.abs((current.y()-10)*3-(current.x()-40)*4)<1e-8
                                && Math.abs((point.y()-10)*3-(point.x()-40)*4)<1e-8;
                        assertTrue(first || second || third, "Refill changed artwork or painted a pen-up gap");
                    }
                    current=point;
                }
            }
            assertEquals(15, total, 1e-8);
        }
    }

    private static void assertPaintedPath(Layer layer, double expected, double limit) {
        Point current = new Point(0, 0);
        double total = 0, sinceRefill = 0;
        for (var command : layer.commands()) {
            if (command instanceof MoveCommand move) current = new Point(move.x, move.y);
            else if (command instanceof RefillCommand) sinceRefill = 0;
            else if (command instanceof DrawCommand draw) for (Point target : draw.points) {
                double distance = Math.hypot(target.x()-current.x(), target.y()-current.y());
                assertTrue(target.x() >= current.x(), "Refill must not reorder or retrace artwork");
                total += distance; sinceRefill += distance;
                assertTrue(sinceRefill <= limit + 1e-8, "Paint distance exceeds refill budget");
                current = target;
            }
        }
        assertEquals(expected, total, 1e-8);
    }
}
