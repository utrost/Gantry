package org.trostheide.gantry.app.session;

import org.trostheide.gantry.model.Bounds;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Metadata;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Physical-mm primitives and a bundled single-stroke technical alphabet. */
public final class QuickArtworkGenerator {
    private static final Map<Character, String> GLYPHS = Map.ofEntries(
            Map.entry('A', "0,7 2.5,0 5,7;1,4 4,4"),
            Map.entry('B', "0,7 0,0 3.5,0 5,1 5,2.5 3.5,3.5 0,3.5;3.5,3.5 5,4.5 5,6 3.5,7 0,7"),
            Map.entry('C', "5,0.5 3.5,0 1,0 0,1.5 0,5.5 1,7 3.5,7 5,6.5"),
            Map.entry('D', "0,7 0,0 3,0 5,2 5,5 3,7 0,7"),
            Map.entry('E', "5,0 0,0 0,7 5,7;0,3.5 4,3.5"),
            Map.entry('F', "0,7 0,0 5,0;0,3.5 4,3.5"),
            Map.entry('G', "5,1 4,0 1,0 0,1 0,6 1,7 4,7 5,6 5,4 3,4"),
            Map.entry('H', "0,0 0,7;5,0 5,7;0,3.5 5,3.5"),
            Map.entry('I', "0,0 5,0;2.5,0 2.5,7;0,7 5,7"),
            Map.entry('J', "5,0 5,5.5 4,7 1,7 0,5.5"),
            Map.entry('K', "0,0 0,7;5,0 0,4 5,7"),
            Map.entry('L', "0,0 0,7 5,7"),
            Map.entry('M', "0,7 0,0 2.5,3.5 5,0 5,7"),
            Map.entry('N', "0,7 0,0 5,7 5,0"),
            Map.entry('O', "1,0 4,0 5,1 5,6 4,7 1,7 0,6 0,1 1,0"),
            Map.entry('P', "0,7 0,0 4,0 5,1 5,2.5 4,3.5 0,3.5"),
            Map.entry('Q', "1,0 4,0 5,1 5,6 4,7 1,7 0,6 0,1 1,0;3,5 5,7"),
            Map.entry('R', "0,7 0,0 4,0 5,1 5,2.5 4,3.5 0,3.5;2.5,3.5 5,7"),
            Map.entry('S', "5,0.5 4,0 1,0 0,1 0,2.5 1,3.5 4,3.5 5,4.5 5,6 4,7 1,7 0,6.5"),
            Map.entry('T', "0,0 5,0;2.5,0 2.5,7"),
            Map.entry('U', "0,0 0,5.5 1,7 4,7 5,5.5 5,0"),
            Map.entry('V', "0,0 2.5,7 5,0"),
            Map.entry('W', "0,0 1,7 2.5,3.5 4,7 5,0"),
            Map.entry('X', "0,0 5,7;5,0 0,7"),
            Map.entry('Y', "0,0 2.5,3.5 5,0;2.5,3.5 2.5,7"),
            Map.entry('Z', "0,0 5,0 0,7 5,7"),
            Map.entry('0', "1,0 4,0 5,1 5,6 4,7 1,7 0,6 0,1 1,0;0,7 5,0"),
            Map.entry('1', "1,1 2.5,0 2.5,7;0,7 5,7"),
            Map.entry('2', "0,1 1,0 4,0 5,1 5,2.5 0,7 5,7"),
            Map.entry('3', "0,0 5,0 2.5,3.5 5,3.5 5,6 4,7 0,7"),
            Map.entry('4', "4,7 4,0 0,5 5,5"),
            Map.entry('5', "5,0 0,0 0,3.5 4,3.5 5,4.5 5,6 4,7 0,7"),
            Map.entry('6', "5,0 1,0 0,1 0,6 1,7 4,7 5,6 5,4.5 4,3.5 0,3.5"),
            Map.entry('7', "0,0 5,0 1,7"),
            Map.entry('8', "1,0 4,0 5,1 5,2.5 4,3.5 1,3.5 0,2.5 0,1 1,0;1,3.5 0,4.5 0,6 1,7 4,7 5,6 5,4.5 4,3.5"),
            Map.entry('9', "5,3.5 1,3.5 0,2.5 0,1 1,0 4,0 5,1 5,6 4,7 0,7"),
            Map.entry('-', "0,3.5 5,3.5"),
            Map.entry('_', "0,7 5,7"),
            Map.entry('+', "0,3.5 5,3.5;2.5,1 2.5,6"),
            Map.entry('=', "0,2.5 5,2.5;0,4.5 5,4.5"),
            Map.entry('/', "0,7 5,0"),
            Map.entry(':', "2.5,2 2.5,2.2;2.5,5.5 2.5,5.7"),
            Map.entry('.', "2.5,6.7 2.5,7"),
            Map.entry(',', "2.5,6.5 2,7"),
            Map.entry('!', "2.5,0 2.5,5;2.5,6.5 2.5,7"),
            Map.entry('?', "0,1 1,0 4,0 5,1 5,2 2.5,4 2.5,5;2.5,6.5 2.5,7"),
            Map.entry('(', "4,0 2,1 1,3.5 2,6 4,7"),
            Map.entry(')', "1,0 3,1 4,3.5 3,6 1,7")
    );

    private QuickArtworkGenerator() { }

    public static ProcessorOutput generate(QuickArtwork spec) {
        List<Command> commands = new ArrayList<>();
        Bounds bounds;
        String label;
        switch (spec.kind()) {
            case LINE -> {
                line(commands, new Point(0, 0), List.of(new Point(spec.widthMm(), spec.heightMm())));
                bounds = new Bounds(0, 0, spec.widthMm(), spec.heightMm());
                label = "Quick line";
            }
            case RECTANGLE -> {
                line(commands, new Point(0, 0), List.of(new Point(spec.widthMm(), 0),
                        new Point(spec.widthMm(), spec.heightMm()), new Point(0, spec.heightMm()), new Point(0, 0)));
                bounds = new Bounds(0, 0, spec.widthMm(), spec.heightMm());
                label = "Quick rectangle";
            }
            case ELLIPSE -> {
                List<Point> points = new ArrayList<>();
                for (int i = 1; i <= 64; i++) {
                    double angle = 2 * Math.PI * i / 64;
                    points.add(new Point(spec.widthMm() / 2 * (1 + Math.cos(angle)),
                            spec.heightMm() / 2 * (1 + Math.sin(angle))));
                }
                line(commands, new Point(spec.widthMm(), spec.heightMm() / 2), points);
                bounds = new Bounds(0, 0, spec.widthMm(), spec.heightMm());
                label = "Quick ellipse";
            }
            case TEXT -> {
                for (char character : spec.text().toCharArray()) {
                    char upper = Character.toUpperCase(character);
                    if (character != ' ' && !(character >= 'a' && character <= 'z')
                            && !(character >= 'A' && character <= 'Z')
                            && !GLYPHS.containsKey(upper))
                        throw new IllegalArgumentException("Unsupported stroke-font character: '" + character
                                + "'. Use A-Z, 0-9 or basic punctuation.");
                }
                String text = spec.text().toUpperCase(Locale.ROOT);
                double unit = spec.heightMm() / 7;
                for (int i = 0; i < text.length(); i++) {
                    char character = text.charAt(i);
                    if (character == ' ') continue;
                    String glyph = GLYPHS.get(character);
                    if (glyph == null) throw new IllegalArgumentException(
                            "Unsupported stroke-font character: '" + character + "'. Use A-Z, 0-9 or basic punctuation.");
                    double startX = i * 6 * unit;
                    for (String stroke : glyph.split(";")) {
                        String[] vertices = stroke.split(" ");
                        List<Point> points = new ArrayList<>(vertices.length);
                        for (String vertex : vertices) {
                            String[] coordinates = vertex.split(",");
                            points.add(new Point(startX + Double.parseDouble(coordinates[0]) * unit,
                                    Double.parseDouble(coordinates[1]) * unit));
                        }
                        line(commands, points.get(0), points.subList(1, points.size()));
                    }
                }
                bounds = new Bounds(0, 0, (text.length() * 6 - 1) * unit, spec.heightMm());
                label = "Quick text: " + (spec.text().length() > 20 ? spec.text().substring(0, 20) + "…" : spec.text());
            }
            default -> throw new IllegalArgumentException("Unknown quick-artwork type");
        }
        return new ProcessorOutput(new Metadata(label, Instant.now(), "default", "mm",
                commands.size(), bounds), List.of(new Layer("Quick", "default", List.copyOf(commands))));
    }

    private static void line(List<Command> commands, Point start, List<Point> rest) {
        int id = commands.size() + 1;
        commands.add(new MoveCommand(id, start.x(), start.y()));
        commands.add(new DrawCommand(id + 1, List.copyOf(rest)));
    }
}
