# Gantry release checklist

## Build and stage a candidate

1. Use `MAJOR.MINOR.PATCH-rc.N` (RC 1–98) or `MAJOR.MINOR.PATCH`. Preserve
   published tags. The next installer candidate is `1.0.0-rc.2`.
2. Write `docs/release-notes/<version>.md` and a truthful acceptance record in
   `release-results/<version>.md`.
3. Run `python scripts/test_release.py` and
   `python scripts/release.py <version> --installer` on each target host.
   Prerequisites: Python 3.9+, Maven, JDK 21+ with jpackage; WiX 3 on Windows;
   fakeroot and DEB build tools on Ubuntu. Output is `dist/<version>/`.
4. Pull requests run installer builds on Windows Server 2022 and Ubuntu 22.04,
   including native upgrades, profile preservation, installed workflow checks,
   native GUI launch with bundled Java, and uninstall.
   Java 17/21 unit tests also run in the separate CI workflow. Manual workflow
   dispatch builds artifacts without publishing a release.
5. After checks pass, tag `v<version>` and push. The tag workflow stages a
   **draft** release only after both platform jobs succeed. Review and publish
   that draft when the candidate acceptance evidence is satisfactory.

Required draft assets:

- `Gantry-<version>.jar` and `Gantry-CLI-<version>.jar`;
- `Gantry-<version>-windows-x64.msi`;
- `Gantry-<version>-linux-x64.deb`;
- `Gantry-<version>-docs.zip` (complete offline guide, images and samples);
- `SHA256SUMS`, `LICENSE`, `README.md`, and `VERSION.txt`.

The consolidated checksum file covers all eight other assets. The GUI manifest,
About dialog and support diagnostics must identify the same release. Windows
candidates are unsigned until a signing certificate/workflow is configured;
do not describe them as signed or verified by Microsoft.

## Installer version and upgrade policy

Product versions remain normal release labels, e.g. `1.0.0-rc.2`. Native
installers require sortable numeric versions. Both platforms use
`major.minor.(patch * 100 + sequence)`, with RC number as the sequence and
99 reserved for stable. Thus RC2 is installer `1.0.2`, stable 1.0.0 is `1.0.99`,
and 1.0.1 RC1 is `1.0.101`. This avoids equal-version or downgrade problems when
moving from an RC to stable. Major/minor are limited to 255 and patch to 654.
Windows installers use per-user scope and include GUI, native CLI, offline help,
samples and license/version files. See [installer contents](packaging/README.md).
Keep the Windows upgrade UUID in `scripts/release.py` unchanged.

## Candidate acceptance

Record these on Windows 10/11 and Ubuntu 22.04/24.04 x86-64:

- Install, application-menu launch and uninstall; no system Java required.
- Windows current-user registration, GUI/help Start menu entries, native CLI
  conversion, complete offline guide/images/samples, and no CLI shortcut.
- About and Copy Diagnostics report the candidate version.
- Guided first plot, SVG/raster import, mock plotting and G-code export.
- Settings/history persist across launch directories and upgrades; recovery
  works after an interrupted session and does not reappear after dismissal.
- Upgrade an earlier installer candidate while preserving the user profile.
- Serial ports enumerate; serial access permissions are documented.

CI launch smoke checks verify window creation and embedded identity with an
isolated mock profile. They do not substitute for the guided interactive journey,
real hardware, Windows 10/11 testing, or upgrade acceptance.

## Hardware-ready / stable release

Complete the mock acceptance suite from `TESTING.md` and record real-machine
connect/disconnect, home/jog/limits, pen lift, frame, stop/alarm recovery, scale
calibration, configured travel speed and a small pen plot. Include station dry/wet
visits and a watercolor job if watercolor readiness is claimed.

Record the exact installer, OS, controller and results in `release-results/`
and `KNOWN_GOOD_SETUPS.md`. Promote to stable only when required checks have no
unexplained failures or blocked safety tests. Novice/adoption claims require
actual participant evidence from `NOVICE_STUDY.md`.

## Installed workflow and upgrade evidence

The package job also builds a **synthetic** `1.0.0-rc.1` installer using the
candidate's code with an older manifest/native identity. It lives under
`dist/upgrade-fixture/<candidate>/<baseline>/` and is never uploaded as a release
asset. This verifies installer replacement/version ordering; it is not a claim
that the historical RC1 application was tested with an installer.

CI installs that fixture, seeds an isolated profile with spaces in its path,
redirects APPDATA/XDG_CONFIG_HOME into that isolated workspace and verifies the
normal OS profile resolver, upgrades to the candidate, and verifies the installed product version and menu
shortcuts. The upgraded launcher runs from a different working directory, with
system Java removed from its environment. Four persisted files (configuration,
history, recovery and editable project) must retain their SHA-256 hashes across
the upgrade, verification and uninstall. Windows must have exactly one Gantry
product registration after upgrade. Uninstall must remove the launcher and menu
shortcut while retaining the isolated user files.

The built-in `--self-test seed|verify <isolated-profile-directory> <report.json>`
command exercises the shipped SVG importer, PNG centerline vectorizer, mock
plot completion/cancellation, G-code exporter and persistence. Seed refuses a
nonempty profile; verify requires the self-test marker. It constructs only mock
and file backends. It writes a JSON report and uses a nonzero exit status for a
failure. It does not click through the GUI or communicate with a controller.

For a local extracted/native launcher, without installing into the system:

```bash
python scripts/accept_installer.py /path/to/Gantry 1.0.0-rc.2 /tmp/gantry-acceptance seed
python scripts/accept_installer.py /path/to/Gantry 1.0.0-rc.2 /tmp/gantry-acceptance verify
```

To build both installers on the host for the CI upgrade check:

```bash
python scripts/release.py 1.0.0-rc.2 --installer --upgrade-from 1.0.0-rc.1
```

`test_installer_upgrade.py` changes system installations and is restricted to
fresh disposable CI hosts. It refuses to replace a preexisting Gantry launcher.
Reports, native installation logs, exported G-code, vectorized SVG and profile
hashes are attached to CI as `validation-<runner>` artifacts. Inspect
`upgrade-result.json`, `seed.json`, and `verify.json`; a green unit test suite
alone does not establish that an upgrade was attempted or completed.
