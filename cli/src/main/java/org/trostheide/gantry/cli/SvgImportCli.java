package org.trostheide.gantry.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.pipeline.io.ProcessorOutputIO;
import org.trostheide.gantry.pipeline.svgimport.PaperFormat;
import org.trostheide.gantry.pipeline.svgimport.SvgImportOptions;
import org.trostheide.gantry.pipeline.svgimport.SvgImportStage;

import java.io.File;

/**
 * Headless SVG-to-commands converter: the Gantry replacement for the legacy
 * {@code WatercolorProcessor} CLI. Converts an SVG file to a {@link ProcessorOutput} command
 * JSON file, ready to be loaded and plotted by the GUI or {@code PlotService}.
 */
public final class SvgImportCli {

    private SvgImportCli() {
    }

    public static void main(String[] args) {
        Options options = new Options();
        options.addOption(Option.builder("i").longOpt("input").hasArg().required(true)
                .desc("Input SVG file.").build());
        options.addOption(Option.builder("o").longOpt("output").hasArg().required(true)
                .desc("Output commands JSON file.").build());
        options.addOption(Option.builder("d").longOpt("max-dist").hasArg()
                .desc("Max draw distance (mm) before a REFILL is inserted. <= 0 disables refill (default 0).").build());
        options.addOption(Option.builder("s").longOpt("station").hasArg()
                .desc("Default refill station ID for layers with no explicit station (default 'default_station').").build());
        options.addOption(Option.builder("c").longOpt("curve-step").hasArg()
                .desc("Curve linearization step (mm, default 0.5).").build());
        options.addOption(Option.builder("f").longOpt("fit-to").hasArg()
                .desc("Fit to paper format: A5, A4, A3, XL, or WxH (mm).").build());
        options.addOption(Option.builder("p").longOpt("padding").hasArg()
                .desc("Padding for --fit-to (mm, default 10.0).").build());
        options.addOption(Option.builder("m").longOpt("mirror")
                .desc("Mirror the drawing horizontally.").build());
        options.addOption(Option.builder("h").longOpt("help").desc("Show help.").build());

        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);
            if (cmd.hasOption("h")) {
                printHelp(options);
                return;
            }

            File inputFile = new File(cmd.getOptionValue("input"));
            File outputFile = new File(cmd.getOptionValue("output"));
            if (!inputFile.exists()) {
                throw new IllegalArgumentException("Input file missing: " + inputFile);
            }

            double maxDrawDistance = Double.parseDouble(cmd.getOptionValue("max-dist", "0"));
            double curveStep = Double.parseDouble(cmd.getOptionValue("curve-step", "0.5"));
            if (curveStep <= 0.01) {
                throw new IllegalArgumentException("Curve step too small.");
            }
            String defaultStationId = cmd.getOptionValue("station", "default_station");
            double padding = Double.parseDouble(cmd.getOptionValue("padding", "10.0"));
            boolean mirror = cmd.hasOption("mirror");
            PaperFormat fitTo = PaperFormat.fromString(cmd.getOptionValue("fit-to"));

            SvgImportOptions importOptions = fitTo != null
                    ? SvgImportOptions.fitToFormat(maxDrawDistance, defaultStationId, curveStep, fitTo, padding, mirror)
                    : new SvgImportOptions(maxDrawDistance, defaultStationId, curveStep, 0, 0, true, 0, 0, mirror);

            ProcessorOutput output = SvgImportStage.importSvg(inputFile, importOptions);
            ProcessorOutputIO.save(output, outputFile);

            System.out.printf("Wrote %d layer(s), %d command(s) to %s%n",
                    output.layers().size(), output.metadata().totalCommands(), outputFile);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            printHelp(options);
            System.exit(1);
        }
    }

    private static void printHelp(Options options) {
        new HelpFormatter().printHelp("gantry-cli", options);
    }
}
