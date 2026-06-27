package org.trostheide.gantry.cli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Headless front stage: raster image (JPG/PNG) → SVG, optionally chained straight
 * into {@link SvgImportCli} so a single command goes image → plottable command model.
 *
 * <p>This is a thin front controller over two existing CLIs and adds no options of
 * its own. Arguments are split on a literal {@code --}:
 *
 * <ul>
 *   <li>everything <em>before</em> {@code --} is passed verbatim to the
 *       {@code vectorize} module's CLI ({@link org.trostheide.gantry.vectorize.Main}),
 *       which traces the image and writes an SVG (see its {@code -s} strategy and
 *       per-strategy options);</li>
 *   <li>everything <em>after</em> {@code --} is passed verbatim to
 *       {@link SvgImportCli}, which converts that SVG into a command JSON. The SVG
 *       just produced is injected as the import's {@code -i} input when the import
 *       arguments don't specify one.</li>
 * </ul>
 *
 * <p>Examples:
 * <pre>{@code
 * # image -> SVG only
 * VectorizeCli -i photo.jpg -o photo.svg -s dp --canny-auto
 *
 * # image -> SVG -> command JSON, fit to A4, in one command
 * VectorizeCli -i photo.jpg -o photo.svg -s centerline -- -o photo.json --fit-to A4
 * }</pre>
 *
 * <p>Chaining requires an explicit {@code -o}/{@code --output} SVG path before
 * {@code --} so the produced SVG can be located deterministically.
 */
public final class VectorizeCli {

    private VectorizeCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        int sep = indexOf(args, "--");
        String[] vectorizeArgs = (sep < 0) ? args : Arrays.copyOfRange(args, 0, sep);

        if (vectorizeArgs.length == 0) {
            System.err.println("No vectorize arguments before '--'. See usage:");
            printUsage();
            System.exit(2);
            return;
        }

        // Stage 1: image -> SVG. Delegates to the vectorize module's CLI, reusing all
        // of its strategy options. It returns on success and System.exit(1)s on error.
        org.trostheide.gantry.vectorize.Main.main(vectorizeArgs);

        if (sep < 0) {
            return; // no chaining requested
        }

        // Stage 2: SVG -> command JSON via the existing SvgImportCli.
        String svgPath = svgOutputPath(vectorizeArgs);
        if (svgPath == null) {
            System.err.println(
                    "Chaining into SVG import requires an explicit -o/--output SVG path "
                            + "in the vectorize arguments (before '--').");
            System.exit(2);
            return;
        }

        String[] importArgs = Arrays.copyOfRange(args, sep + 1, args.length);
        importArgs = ensureInput(importArgs, svgPath);
        SvgImportCli.main(importArgs);
    }

    /** First index of {@code token} in {@code args}, or -1. */
    private static int indexOf(String[] args, String token) {
        for (int i = 0; i < args.length; i++) {
            if (token.equals(args[i])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Mirrors the vectorize CLI's output-path rule: the value of {@code -o}/{@code --output}
     * is used as-is when it ends in {@code .svg}, otherwise {@code .svg} is appended.
     * Returns {@code null} when no output option is present.
     */
    private static String svgOutputPath(String[] vectorizeArgs) {
        for (int i = 0; i < vectorizeArgs.length - 1; i++) {
            String a = vectorizeArgs[i];
            if ("-o".equals(a) || "--output".equals(a)) {
                String value = vectorizeArgs[i + 1];
                return value.toLowerCase().endsWith(".svg") ? value : value + ".svg";
            }
        }
        return null;
    }

    /** Prepends {@code -i <svgPath>} unless the import args already supply an input. */
    private static String[] ensureInput(String[] importArgs, String svgPath) {
        for (String a : importArgs) {
            if ("-i".equals(a) || "--input".equals(a)) {
                return importArgs;
            }
        }
        List<String> withInput = new ArrayList<>();
        withInput.add("-i");
        withInput.add(svgPath);
        withInput.addAll(Arrays.asList(importArgs));
        return withInput.toArray(new String[0]);
    }

    private static void printUsage() {
        System.out.println("""
                Gantry vectorize — raster image (JPG/PNG) -> SVG, optionally -> command JSON.

                Usage:
                  VectorizeCli <vectorize-args...> [-- <svg-import-args...>]

                Without '--':  runs only the image -> SVG vectorizer.
                With '--':     chains the produced SVG into the SVG -> commands import;
                               the SVG is injected as the import's -i input.
                               An explicit -o <file>.svg is required before '--'.

                Vectorize args (image -> SVG) and import args (SVG -> commands) are the
                options of the standalone vectorizer and of SvgImportCli respectively;
                run each with -h to list them.

                Examples:
                  VectorizeCli -i photo.jpg -o photo.svg -s dp --canny-auto
                  VectorizeCli -i photo.jpg -o photo.svg -s centerline -- -o photo.json --fit-to A4
                """);
    }
}
