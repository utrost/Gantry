package org.trostheide.watercolorprocessor;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;

public class WatercolorProcessor {

    private static final Logger logger = LoggerFactory.getLogger(WatercolorProcessor.class);

    public static void main(String[] args) {
        Options options = new Options();

        options.addOption(Option.builder("i").longOpt("input").hasArg().required(true).desc("Input SVG file.").build());
        options.addOption(
                Option.builder("o").longOpt("output").hasArg().required(true).desc("Output JSON file.").build());
        options.addOption(Option.builder("d").longOpt("max-dist").hasArg().required(true)
                .desc("Max draw distance (mm).").build());

        // Changed: Station is no longer strictly required by CLI, as layers might
        // define it.
        // We will treat it as a required DEFAULT if layers are unnamed.
        options.addOption(Option.builder("s").longOpt("station")
                .hasArg()
                .desc("Default Refill Station ID (used if layers are unnamed).")
                .build());

        options.addOption(
                Option.builder("c").longOpt("curve-step").hasArg().desc("Curve linearization step (mm).").build());
        options.addOption(
                Option.builder("f").longOpt("fit-to").hasArg().desc("Fit to paper format (A4, A3, A5, XL).").build());
        options.addOption(
                Option.builder("p").longOpt("padding").hasArg().desc("Padding for fit-to (mm). Default 10.").build());
        options.addOption(Option.builder("h").longOpt("help").desc("Help").build());

        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);
            if (cmd.hasOption("h")) {
                printHelp(options);
                return;
            }

            File inputFile = new File(cmd.getOptionValue("input"));
            File outputFile = new File(cmd.getOptionValue("output"));
            double maxDrawDistance = Double.parseDouble(cmd.getOptionValue("max-dist"));
            double curveApproximation = Double.parseDouble(cmd.getOptionValue("curve-step", "0.5"));

            // Get default station or "unknown"
            String defaultStationId = cmd.getOptionValue("station", "default_station");

            validateInputs(inputFile, maxDrawDistance, curveApproximation);

            String fitToFormat = cmd.getOptionValue("fit-to");
            double padding = Double.parseDouble(cmd.getOptionValue("padding", "10.0"));

            logger.info(
                    "Configuration Verified: Input={}, Output={}, MaxDist={}, DefaultStation={}, FitTo={}, Padding={}",
                    inputFile.getName(), outputFile.getName(), maxDrawDistance, defaultStationId, fitToFormat, padding);

            ProcessorService service = new ProcessorService();
            service.process(inputFile, outputFile, maxDrawDistance, defaultStationId, curveApproximation, fitToFormat,
                    padding, false);

            logger.info("Processing complete.");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            printHelp(options);
            System.exit(1);
        }
    }

    private static void printHelp(Options options) {
        new HelpFormatter().printHelp("watercolor-processor", options);
    }

    private static void validateInputs(File in, double dist, double curve) {
        if (!in.exists())
            throw new IllegalArgumentException("Input file missing: " + in);
        if (dist <= 0)
            throw new IllegalArgumentException("Max distance must be positive.");
        if (curve <= 0.01)
            throw new IllegalArgumentException("Curve step too small.");
    }
}