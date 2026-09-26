# Installer contents and downloads

Windows is the priority desktop platform. The native installer is the primary
user download; portable JARs are a separate choice.

## Windows installation

The MSI installs for the current Windows user, normally under
`%LOCALAPPDATA%\Gantry`. Installation offers a directory chooser and optional
shortcuts. It includes:

- `Gantry.exe`: GUI, with a Start menu entry;
- `gantry-cli.exe`: terminal launcher using the bundled Java runtime, with no
  desktop or Start menu shortcut;
- `Gantry Help.exe`: opens the local guide, with a Start menu entry;
- bundled Java 21 runtime;
- GUI and CLI JARs under `app/`;
- `app/docs/index.html`, all referenced screenshots, and four sample SVGs;
- `app/LICENSE` and `app/VERSION.txt`.

The GUI also offers **Help > Getting Started (Offline)…**. The help launcher
resolves the guide beside its JAR, so it works from other working directories
and chosen installation paths containing spaces. Portable JAR users can extract
the matching documentation ZIP beside the GUI JAR to use the same Help action.

The native CLI does not modify PATH. Use its full path, or add the installation
directory to your user PATH manually. No automatic PATH change is included in
this candidate. The CLI requires no separate Java installation.

Settings, history and recovery use `%APPDATA%\Gantry`; save user projects outside
the installation directory. Uninstall removes the installed application and
shortcuts while preserving the user profile and saved work.

Earlier experimental CI installers used machine-wide scope. They were not
published releases. Remove one of those experimental machine-wide installations
before switching to this per-user candidate; scope migration is not claimed.
The synthetic upgrade test now validates per-user-to-per-user replacement.

## Download artifacts

| Artifact | Contents |
|---|---|
| `release-windows` | MSI, standalone README, LICENSE, VERSION.txt, matching SHA256SUMS |
| `release-linux` | DEB, standalone README, LICENSE, VERSION.txt, matching SHA256SUMS |
| `release-portable` | GUI/CLI JARs, complete docs ZIP, README, LICENSE, VERSION.txt, matching SHA256SUMS |

The README is generated for the release and has no image references or relative
repository documentation links. Tagged drafts consolidate all assets and
recompute checksums. The docs ZIP contains its own `docs/`, license and version
files; extract the full archive to retain all guide links.

## Build and verification

Installer builds require JDK 21+, Python 3.9+, Maven, and native packaging tools
(WiX 3 on Windows). Java source/portable artifacts remain compatible with Java 17.
JDK 21 is needed to control shortcuts independently for additional launchers;
see the [official packaging guide](https://docs.oracle.com/en/java/javase/21/jpackage/support-application-features.html).

`release_payload.py` stages and validates every relative guide link.
`verify_payload.py` checks the installed resources, runs native CLI help and SVG
conversion with system Java removed from PATH, and verifies the help launcher's
local guide resolution. Native upgrade CI queries Windows Installer’s product context to verify per-user
scope (rather than inferring scope from registry hive views), checks
both Start menu shortcuts, and cleanup/profile preservation after uninstall.

Physical Windows 10/11 desktop acceptance, browser launch, signing, and plotter
hardware checks remain separate from automated package validation.
