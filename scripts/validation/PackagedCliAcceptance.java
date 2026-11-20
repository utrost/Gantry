import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.trostheide.gantry.pipeline.io.ProcessorOutputIO;
import org.trostheide.gantry.model.command.DrawCommand;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Subprocess checks use only the shaded CLI JAR, never Maven's dependency classpath. */
public class PackagedCliAcceptance {
    static Path jar, results, source;
    static final ObjectMapper JSON = new ObjectMapper();
    static int invocation;
    record Result(int exit, String log) { }
    public static void main(String[] args) throws Exception {
        jar = Path.of(args[0]).toAbsolutePath();
        results = Path.of(args[1]).toAbsolutePath();
        Files.createDirectories(results);
        source = results.resolve("caf\u00e9.png");
        Files.copy(Path.of(args[2]), source, StandardCopyOption.REPLACE_EXISTING);
        pngAndErrors();
        recipesAndOverrides();
        batches();
        Files.writeString(results.resolve("result.json"), "{\"issues\":[84,88,89,90,91],\"result\":\"passed\"}\n");
        System.out.println("PASS packaged CLI PNG, complete recipe rerun, explicit overrides, batch failure and collision/source preservation");
    }
    static void pngAndErrors() throws Exception {
        Path svg = results.resolve("render.svg"), commands = results.resolve("render.commands.json");
        success("-i", source.toString(), "-o", svg.toString(), "-s", "sketch", "--format", "PNG",
                "--", "-o", commands.toString(), "--fit-to", "180x180");
        BufferedImage image = ImageIO.read(results.resolve("render.png").toFile());
        check(image != null && image.getWidth() == 512 && image.getHeight() == 512, "PNG decodes at source size");
        int ink = 0, blank = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int argb = image.getRGB(x, y);
            if ((argb >>> 24) > 0 && (argb & 0xffffff) < 0xeeeeee) ink++;
            else blank++;
        }
        check(ink > 100 && blank > 100, "PNG contains both drawing marks and empty background");
        var output = ProcessorOutputIO.load(commands.toFile());
        check(output.layers().stream().flatMap(l -> l.commands().stream())
                .anyMatch(c -> c instanceof DrawCommand d && !d.points.isEmpty()), "PNG chain emits real command geometry");
        for (String format : List.of("pdf", "jpeg")) {
            Path target = results.resolve("unsupported-" + format + ".svg");
            Result result = run("-i", source.toString(), "-o", target.toString(), "-s", "sketch", "--format", format);
            check(result.exit != 0 && result.log.toLowerCase(Locale.ROOT).contains(format), "unsupported format fails clearly");
            check(!Files.exists(target), "unsupported format fails before creating SVG");
        }
    }
    static void recipesAndOverrides() throws Exception {
        for (String mode : List.of("squiggle", "needles", "isolines", "sketch", "centerline", "pbn")) {
            Path original = results.resolve("recipe-" + mode + ".svg");
            List<String> args = new ArrayList<>(List.of("-i", source.toString(), "-o", original.toString(),
                    "-s", mode, "-t", "1.7", "-d", "0.7", "--crop", "10,10,300,300",
                    "--stroke-color", "#123456", "--stroke-width", "0.8", "--cl-threshold", "170"));
            switch (mode) {
                case "squiggle" -> args.addAll(List.of("--squiggle-density", "1.8", "--squiggle-amplitude", "2.5",
                        "--squiggle-tone", "0.9", "--squiggle-background", "0"));
                case "needles" -> args.addAll(List.of("--needle-spacing", "9", "--needle-length", "13", "--needle-gradient", "0.08"));
                case "isolines" -> args.addAll(List.of("--isoline-levels", "4", "--isoline-smoothing", "2"));
                case "sketch" -> args.addAll(List.of("--sketch-window", "17", "--sketch-offset", "0.08", "--sketch-skeleton", "false"));
                case "pbn" -> args.addAll(List.of("--pbn-num-colors", "3", "--pbn-min-area", "40", "--pbn-font-size", "9", "--pbn-no-numbers", "--pbn-no-legend"));
            }
            success(args.toArray(String[]::new));
            Path rerun = results.resolve("rerun-" + mode + ".svg");
            success("--config", original + ".json", "--output", rerun.toString());
            check(Files.readString(original).equals(Files.readString(rerun)), mode + " actual SVG geometry/style/crop round-trips exactly");
            check(read(Path.of(rerun + ".json")).path("crop").asText().equals("10,10,300,300"), "crop retained");
        }
        Path target = results.resolve("override.svg");
        success("--config", results.resolve("recipe-sketch.svg.json").toString(), "--output", target.toString(),
                "--strategy", "needles", "--needle-spacing", "11", "--stroke-color", "#ff0000", "--detail", "0.4");
        JsonNode metadata = read(Path.of(target + ".json"));
        check(metadata.path("strategy").asText().equals("needles"), "explicit strategy overrides saved value");
        check(metadata.at("/needles/spacing").asDouble() == 11 && metadata.path("detail").asDouble() == .4,
                "explicit numeric overrides apply");
        check(metadata.path("strokeColor").asText().equals("#ff0000") && metadata.path("strokeWidth").asDouble() == .8,
                "explicit style replaces one field and preserves unspecified settings");
        Path second = results.resolve("second.png");
        makeImage(second, "png");
        success("--config", results.resolve("recipe-sketch.svg.json").toString(), "-i", second.toString(),
                "-o", results.resolve("source-override.svg").toString(), "--crop", "0,0,60,60");
        check(read(results.resolve("source-override.svg.json")).path("sourceImage").asText().equals(second.toString()), "source override applies");
    }
    static void batches() throws Exception {
        Path input = results.resolve("mixed"), output = results.resolve("mixed-out");
        Files.createDirectories(input);
        Files.copy(source, input.resolve("z-good.png"));
        Files.writeString(input.resolve("a-broken.png"), "not an image");
        Result mixed = run("--input-dir", input.toString(), "--output-dir", output.toString(), "-s", "sketch");
        check(mixed.exit != 0 && mixed.log.contains("1 succeeded, 1 failed") && mixed.log.contains("a-broken.png"), "partial batch failure is observable");
        check(Files.exists(output.resolve("z-good.svg")), "successful independent artifact survives partial failure");
        check(mixed.log.indexOf("Processing: a-broken") < mixed.log.indexOf("Processing: z-good"), "batch order is deterministic");
        Path empty = results.resolve("empty"); Files.createDirectories(empty);
        check(run("--input-dir", empty.toString()).exit == 0, "empty batch is an explicit no-op");
        check(run("--input-dir", results.resolve("missing").toString()).exit != 0, "missing directory fails");
        Path collision = results.resolve("collision"), collisionOut = results.resolve("collision-out");
        Files.createDirectories(collision); Files.createDirectories(collisionOut);
        Files.copy(source, collision.resolve("same.png")); makeImage(collision.resolve("same.jpg"), "jpg");
        Path sentinel = collisionOut.resolve("same.svg"); Files.writeString(sentinel, "existing artwork");
        Result duplicate = run("--input-dir", collision.toString(), "--output-dir", collisionOut.toString(), "-s", "sketch");
        check(duplicate.exit != 0 && duplicate.log.contains("collision"), "same-basename inputs rejected before writing");
        check(Files.readString(sentinel).equals("existing artwork"), "collision preserves existing output");
        try (var entries = Files.list(collisionOut)) { check(entries.count() == 1, "no partial collision artifacts"); }
        Path caseInputs = results.resolve("case-inputs"), caseOut = results.resolve("case-out");
        Files.createDirectories(caseInputs);
        Files.copy(source, caseInputs.resolve("Case.png")); makeImage(caseInputs.resolve("case.jpg"), "jpg");
        Result caseDuplicate = run("--input-dir", caseInputs.toString(), "--output-dir", caseOut.toString(), "-s", "sketch");
        check(caseDuplicate.exit != 0 && caseDuplicate.log.contains("collision") && !Files.exists(caseOut),
                "case-insensitive output collisions rejected before creating a destination");
        Path raster = results.resolve("raster-source"); Files.createDirectories(raster);
        Path photo = raster.resolve("photo.png"); Files.copy(source, photo);
        byte[] before = Files.readAllBytes(photo);
        Result overwrite = run("--input-dir", raster.toString(), "-s", "sketch", "--format", "png");
        check(overwrite.exit != 0 && overwrite.log.contains("overwrite input"), "raster batch cannot overwrite source");
        check(Arrays.equals(before, Files.readAllBytes(photo)), "source photo bytes preserved");
        try (var entries = Files.list(raster)) { check(entries.count() == 1, "source-overwrite rejection writes no artifacts"); }
        Path rasterOut = results.resolve("raster-out");
        success("--input-dir", raster.toString(), "--output-dir", rasterOut.toString(), "-s", "sketch", "--format", "png");
        check(ImageIO.read(rasterOut.resolve("photo.png").toFile()) != null, "separate-directory PNG batch is supported");
        // Explicit single-image defaults must not override each batch file after option rebuilding.
        Path good = results.resolve("good-batch"), goodOut = results.resolve("good-batch-out"); Files.createDirectories(good);
        Files.copy(source, good.resolve("photo.png")); makeImage(good.resolve("shape.jpg"), "jpg");
        success("--input", source.toString(), "--output", results.resolve("ignored.svg").toString(),
                "--input-dir", good.toString(), "--output-dir", goodOut.toString(), "-s", "sketch");
        check(!Files.readString(goodOut.resolve("photo.svg")).equals(Files.readString(goodOut.resolve("shape.svg"))), "per-file inputs replace long-option defaults");
    }
    static void makeImage(Path path, String format) throws Exception {
        BufferedImage image = new BufferedImage(80, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); g.setColor(Color.WHITE); g.fillRect(0,0,80,80);
        g.setColor(Color.BLACK); g.fillRect(15,15,40,40); g.dispose();
        check(ImageIO.write(image, format, path.toFile()), "fixture encoding supported");
    }
    static JsonNode read(Path path) throws Exception { return JSON.readTree(Files.readString(path)); }
    static void success(String... args) throws Exception {
        Result result = run(args); check(result.exit == 0, "CLI failed: " + result.log);
    }
    static Result run(String... args) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Djava.awt.headless=true", "-cp", jar.toString(), "org.trostheide.gantry.cli.VectorizeCli"));
        command.addAll(List.of(args));
        Path log = results.resolve(String.format(Locale.ROOT, "%02d-cli.log", ++invocation));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(90, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new AssertionError("CLI timed out: " + log); }
        // Native consoles may encode diagnostic paths differently; retain raw logs and
        // decode with replacement for assertions on the ASCII outcome text.
        return new Result(process.exitValue(), new String(Files.readAllBytes(log), java.nio.charset.StandardCharsets.UTF_8));
    }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
