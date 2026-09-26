package org.trostheide.gantry.app.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.trostheide.gantry.app.plot.ConfigStore;
import org.trostheide.gantry.app.plot.GantryConfig;
import org.trostheide.gantry.app.plot.PlotJobHistory;
import org.trostheide.gantry.app.plot.PlotService;
import org.trostheide.gantry.app.plot.UserStateFiles;
import org.trostheide.gantry.app.session.GantryProject;
import org.trostheide.gantry.app.session.GantryProjectIO;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.pipeline.svgimport.SvgImportOptions;
import org.trostheide.gantry.pipeline.svgimport.SvgImportStage;
import org.trostheide.gantry.plotter.GcodeFileBackend;
import org.trostheide.gantry.plotter.GcodeFormatter;
import org.trostheide.gantry.plotter.MockPlotterBackend;
import org.trostheide.gantry.vectorize.Main;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit, isolated installed-runtime checks. Never constructs a serial backend. */
public final class InstallationCheck {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final String MARKER = "gantry-installation-check-v1";

    private InstallationCheck() { }

    public static int execute(String[] args) {
        if (args.length != 3 || !(args[0].equals("seed") || args[0].equals("verify"))) {
            System.err.println("Usage: --self-test seed|verify <isolated-profile-directory> <report.json>");
            return 2;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", InstallationCheck.class.getPackage().getImplementationVersion());
        result.put("phase", args[0]);
        int exit = 0;
        try {
            String expectedConfig = System.getenv("GANTRY_SELF_TEST_EXPECT_CONFIG");
            if (expectedConfig != null) {
                require(ConfigStore.guiConfigFile(null).toPath().equals(Path.of(expectedConfig)),
                        "Default OS profile location did not match the isolated test environment");
                result.put("defaultProfileVerified", true);
            }
            result.put("checks", run(Path.of(args[1]), args[0].equals("seed")));
            result.put("status", "passed");
        } catch (Exception ex) {
            result.put("status", "failed");
            result.put("error", ex.toString());
            ex.printStackTrace();
            exit = 1;
        }
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(Path.of(args[2]).toFile(), result);
        } catch (IOException ex) {
            ex.printStackTrace();
            return 1;
        }
        return exit;
    }

    /** Seed only an empty directory; verify only profiles created by this check. */
    public static List<String> run(Path directory, boolean seed) throws Exception {
        Path profile = directory.toAbsolutePath();
        Files.createDirectories(profile);
        if (seed) {
            try (var entries = Files.list(profile)) {
                require(entries.findAny().isEmpty(), "Self-test seed requires an empty directory");
            }
        } else {
            require(Files.readString(profile.resolve("self-test-profile.txt")).equals(MARKER),
                    "Not a self-test profile");
        }
        String previous = System.getProperty("gantry.config.file");
        try {
            System.setProperty("gantry.config.file", profile.resolve("config.json").toString());
            if (seed) seed(profile);
            return verify(profile);
        } finally {
            if (previous == null) System.clearProperty("gantry.config.file");
            else System.setProperty("gantry.config.file", previous);
        }
    }

    private static void seed(Path profile) throws Exception {
        GantryConfig config = new GantryConfig();
        config.mock = true;
        config.machineOrigin = "Top-Left";
        config.gcode.machineWidth = 321;
        config.gcode.machineHeight = 234;
        config.gcode.feedRateDraw = 600000;
        config.gcode.feedRateTravel = 600000;
        config.gcode.penDownDelayMillis = 0;
        config.lastDirectory = profile.toString();
        ConfigStore.save(config, profile.resolve("config.json").toFile());
        Path svg = profile.resolve("fixture.svg");
        Files.writeString(svg, """
                <svg xmlns="http://www.w3.org/2000/svg" width="40mm" height="30mm" viewBox="0 0 40 30">
                  <path fill="none" stroke="#d62728" d="M5 5 L25 5 L25 20"/>
                  <path fill="none" stroke="#1f77b4" d="M5 10 L15 20"/>
                </svg>
                """);
        ProcessorOutput output = SvgImportStage.importSvg(svg.toFile(), SvgImportOptions.defaults());
        require(draws(output) == 2, "SVG fixture must produce two drawable strokes");
        GantryProject project = new GantryProject(GantryProject.CURRENT_VERSION, output,
                List.of(0), GantryProject.Placement.identity(), 2, GantryProject.Source.empty());
        GantryProjectIO.save(project, profile.resolve("fixture.gantry").toFile());
        GantryProjectIO.save(project, UserStateFiles.resolve(profile.resolve("config.json").toFile(), ".gantry-recovery"));
        PlotJobHistory history = new PlotJobHistory(UserStateFiles.resolve(profile.resolve("config.json").toFile(), "plot-history.json"));
        history.add(new PlotJobHistory.Job(Instant.parse("2026-01-01T00:00:00Z"), output, config.toPlotSettings()));
        Files.writeString(profile.resolve("self-test-profile.txt"), MARKER);
    }

    private static List<String> verify(Path profile) throws Exception {
        List<String> checks = new ArrayList<>();
        var configFile = ConfigStore.guiConfigFile(null);
        require(configFile.toPath().equals(profile.resolve("config.json")), "Profile escaped its directory");
        GantryConfig config = ConfigStore.load(configFile);
        require(config.mock && config.gcode.machineWidth == 321 && config.gcode.machineHeight == 234
                && profile.toString().equals(config.lastDirectory), "Settings were not preserved");
        checks.add("settings-preserved");

        GantryProject project = GantryProjectIO.load(profile.resolve("fixture.gantry").toFile());
        require(project.passes() == 2 && draws(project.output()) == 2, "Editable project was not preserved");
        GantryProject recovered = GantryProjectIO.load(UserStateFiles.resolve(configFile, ".gantry-recovery"));
        require(JSON.valueToTree(project).equals(JSON.valueToTree(recovered)), "Recovery differs from saved project");
        checks.add("project-and-recovery-preserved");
        var jobs = new PlotJobHistory(UserStateFiles.resolve(configFile, "plot-history.json")).jobs();
        require(jobs.size() == 1 && JSON.valueToTree(jobs.get(0).output()).equals(JSON.valueToTree(project.output()))
                && jobs.get(0).settings().machineWidth == 321, "History was not preserved");
        checks.add("history-preserved");

        ProcessorOutput imported = SvgImportStage.importSvg(profile.resolve("fixture.svg").toFile(), SvgImportOptions.defaults());
        require(draws(imported) == 2, "SVG import lost strokes");
        checks.add("svg-import");
        List<String> mockLog = new ArrayList<>();
        MockPlotterBackend mock = new MockPlotterBackend(config.gcode, mockLog::add);
        require(mock.connect(), "Mock connection failed");
        try {
            PlotService service = new PlotService(mock, config.toPlotSettings());
            int[] accepted = {0};
            service.setStrokeProgressCallback(event -> {
                if (event.phase() == PlotService.StrokeProgressPhase.ACCEPTED) accepted[0]++;
            });
            service.plot(imported);
            require(accepted[0] == 2, "Mock plot did not accept every stroke");
            require(mock.queryPosition()[0] == 0 && mock.queryPosition()[1] == 0, "Mock plot did not park");
            checks.add("mock-plot-complete");
            mockLog.clear();
            service.setLayerStartedCallback(layer -> service.cancel());
            service.plot(imported);
            require(mockLog.contains("[Mock] Pen UP") && mockLog.stream().noneMatch(line -> line.contains("Draw to")),
                    "Cancelled mock plot did not stay pen-up");
            checks.add("mock-cancel-pen-up");
        } finally {
            mock.disconnect();
        }

        Path exported = profile.resolve("export.gcode");
        GcodeFileBackend file = new GcodeFileBackend(config.gcode, exported.toFile());
        require(file.connect(), "G-code output did not open");
        try { new PlotService(file, config.toPlotSettings()).plot(imported); }
        finally { file.disconnect(); }
        List<String> gcode = Files.readAllLines(exported);
        require(gcode.contains("G21") && gcode.contains("G90")
                && gcode.stream().filter(line -> line.startsWith("G1 X")).count() >= 4
                && gcode.contains(GcodeFormatter.penUp(config.gcode))
                && gcode.get(gcode.size() - 1).equals("G0 X0 Y0"), "G-code export is incomplete");
        checks.add("gcode-export");

        BufferedImage raster = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        var graphics = raster.createGraphics();
        try {
            graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 64, 64);
            graphics.setColor(Color.BLACK); graphics.setStroke(new BasicStroke(5));
            graphics.drawLine(10, 10, 50, 50);
        } finally { graphics.dispose(); }
        Path image = profile.resolve("fixture.png");
        require(ImageIO.write(raster, "png", image.toFile()), "PNG encoder unavailable");
        Path traced = profile.resolve("traced.svg");
        Main.runSingleFile(new String[]{"-i", image.toString(), "-o", traced.toString(), "-s", "centerline"});
        require(draws(SvgImportStage.importSvg(traced.toFile(), SvgImportOptions.defaults())) > 0,
                "Raster vectorization produced no drawable strokes");
        checks.add("raster-vectorize-import");
        return List.copyOf(checks);
    }

    private static long draws(ProcessorOutput output) {
        return output.layers().stream().flatMap(layer -> layer.commands().stream())
                .filter(command -> command instanceof DrawCommand draw && !draw.points.isEmpty()).count();
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }
}
