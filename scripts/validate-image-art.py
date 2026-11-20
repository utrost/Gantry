#!/usr/bin/env python3
"""Offline real-photo comparison through existing CLIs; Python stdlib + JDK only."""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
CORPUS = ROOT / "docs/validation/image-art"


def read(path):
    return json.loads(path.read_text())


def digest(data):
    return hashlib.sha256(data).hexdigest()


def stable_commands(path):
    value = read(path)
    # Only provenance changes: generated timestamp and the run-specific SVG path.
    value["metadata"].pop("generatedAt", None)
    value["metadata"].pop("source", None)
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def finite(value):
    if isinstance(value, dict):
        return all(finite(v) for v in value.values())
    if isinstance(value, list):
        return all(finite(v) for v in value)
    return not isinstance(value, float) or math.isfinite(value)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, default=ROOT / "cli/target/cli-1.0.0.jar")
    parser.add_argument("--output", type=Path, required=True, help="new directory for bulk artifacts")
    parser.add_argument("--recipes", type=Path, default=CORPUS / "recipes.json")
    parser.add_argument("--baseline", type=Path, help="require the committed geometry/metrics hashes")
    args = parser.parse_args()
    jar = args.jar.resolve()
    out = args.output.resolve()
    if not jar.is_file():
        parser.error("Build the CLI jar with mvn package first")
    out.mkdir(parents=True, exist_ok=False)
    tools = out / "tools"
    tools.mkdir()
    subprocess.run(["javac", "-cp", str(jar), "-d", str(tools),
                    str(ROOT / "scripts/validation/ImageArtArtifactCheck.java")], check=True)
    java = ["java", "-Djava.awt.headless=true", "-Duser.language=en", "-Duser.country=US",
            "-cp", str(jar)]
    recipes = read(args.recipes)
    corpus = read(CORPUS / "corpus.json")
    report = {"jarSha256": digest(jar.read_bytes()), "recipes": recipes,
              "machine": read(CORPUS / "machine.json"), "repetitions": 2, "artifacts": []}
    report["javaVersion"] = subprocess.run(["java", "-version"], capture_output=True, text=True, check=True).stderr.strip()
    metrics_files = []
    for image in corpus["images"]:
        source = CORPUS / image["file"]
        if digest(source.read_bytes()) != image["sha256"]:
            raise ValueError(f"Corpus checksum mismatch: {source}")
        for mode, flags in recipes["modes"].items():
            artifact = image["id"] + "-" + mode
            hashes = []
            for repetition in (1, 2):
                folder = out / f"run-{repetition}"
                folder.mkdir(exist_ok=True)
                base = folder / artifact
                command = java + ["org.trostheide.gantry.cli.VectorizeCli", "-i", str(source),
                                 "-o", str(base.with_suffix(".svg")), "-s", mode]
                command += recipes["commonVectorize"] + flags + ["--", "-o", str(base.with_suffix(".json"))]
                command += recipes["import"] + ["--config", str(CORPUS / "machine.json"),
                           "--gcode", str(base.with_suffix(".gcode")), "--metrics", str(base.with_suffix(".metrics.json"))]
                with base.with_suffix(".log").open("w") as log:
                    log.write(json.dumps(command) + "\n")
                    log.flush()
                    subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=180)
                svg = ET.parse(base.with_suffix(".svg")).getroot()
                if not any(node.tag in ("{http://www.w3.org/2000/svg}path",
                                        "{http://www.w3.org/2000/svg}polyline") for node in svg.iter()):
                    raise ValueError(f"No SVG paths in {artifact}")
                checker = ["java", "-cp", str(tools) + os.pathsep + str(jar), "ImageArtArtifactCheck",
                           str(CORPUS / "machine.json"), str(base.with_suffix(".json")), str(base.with_suffix(".gcode"))]
                checks = json.loads(subprocess.run(checker, capture_output=True, text=True, check=True, timeout=60).stdout)
                metrics = read(base.with_suffix(".metrics.json"))
                if not finite(metrics) or metrics["drawDistanceMm"] <= 0:
                    raise ValueError(f"Invalid metrics: {artifact}")
                hashes.append({"svg": digest(base.with_suffix(".svg").read_bytes()),
                               "commands": digest(stable_commands(base.with_suffix(".json"))),
                               "metrics": digest(json.dumps(metrics, sort_keys=True, allow_nan=False).encode()),
                               "gcode": digest(base.with_suffix(".gcode").read_bytes())})
                if repetition == 1:
                    metrics_files.append(base.with_suffix(".metrics.json"))
            if hashes[0] != hashes[1]:
                raise ValueError(f"Nondeterministic artifacts: {artifact}: {hashes}")
            report["artifacts"].append({"id": artifact, "metrics": metrics, "checks": checks,
                                        "repeatExact": True, "sha256": hashes[0]})
            print(f"PASS {artifact}: exact repeated geometry, finite bounds, G-code preflight", flush=True)
    subprocess.run(java + ["org.trostheide.gantry.cli.ImageArtValidationCli", "-o", str(out / "comparison.json")]
                   + [str(p) for p in metrics_files], check=True)
    if args.baseline:
        expected = {row["id"]: row["sha256"] for row in read(args.baseline)["artifacts"]}
        actual = {row["id"]: row["sha256"] for row in report["artifacts"]}
        if actual != expected:
            raise ValueError("Committed baseline changed; review geometry, previews and metrics before updating it")
    (out / "verification.json").write_text(json.dumps(report, indent=2, allow_nan=False) + "\n")
    print(f"Validated {len(report['artifacts'])} combinations twice. Report: {out / 'verification.json'}")


if __name__ == "__main__":
    main()
