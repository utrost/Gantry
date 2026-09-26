"""Stage complete offline help and write a self-contained download README."""
from html.parser import HTMLParser
from pathlib import Path
import shutil
from urllib.parse import unquote, urlsplit
import zipfile

SAMPLES = ('simple-line.svg', 'multi-colour-layers.svg', 'hatch-fill.svg', 'text-outline.svg')
IMAGES = ('workspace-layer-preview.png', 'first-run-guided-practice.png',
          'preferences-geometry.png', 'layer-watercolor-settings.png')

class _Links(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = []
        self.ids = set()
    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if 'id' in attrs:
            self.ids.add(attrs['id'])
        for key in ('src', 'href'):
            if key in attrs:
                self.links.append(attrs[key])


def validate_help(directory):
    page = directory / 'docs/index.html'
    parser = _Links()
    parser.feed(page.read_text(encoding='utf-8'))
    for link in parser.links:
        url = urlsplit(link)
        if url.scheme or url.netloc:
            continue
        if url.path:
            target = (page.parent / unquote(url.path)).resolve()
            if not target.is_relative_to(directory.resolve()) or not target.is_file():
                raise ValueError(f'Offline help has a missing/escaping link: {link}')
        elif url.fragment and url.fragment not in parser.ids:
            raise ValueError(f'Offline help has a missing anchor: {link}')
    if '@VERSION@' in page.read_text(encoding='utf-8'):
        raise ValueError('Offline help contains an unexpanded release version')


def stage_help(root, directory, version):
    docs = directory / 'docs'
    docs.mkdir(parents=True, exist_ok=True)
    html = (root / 'docs/packaging/getting-started.html').read_text(encoding='utf-8')
    (docs / 'index.html').write_text(html.replace('@VERSION@', version), encoding='utf-8')
    for folder, names in (('samples', SAMPLES), ('images', IMAGES)):
        target = docs / folder
        target.mkdir(exist_ok=True)
        for name in names:
            shutil.copy2(root / 'docs' / folder / name, target / name)
    shutil.copy2(root / 'LICENSE', directory / 'LICENSE')
    (directory / 'VERSION.txt').write_text(
        f'Gantry {version}\nhttps://github.com/utrost/Gantry/releases/tag/v{version}\n', encoding='utf-8')
    validate_help(directory)


def docs_archive(root, output, version):
    # The staged docs stay outside the public asset directory; downloads get one complete ZIP.
    import tempfile
    archive = output / f'Gantry-{version}-docs.zip'
    with tempfile.TemporaryDirectory(prefix='gantry-docs-') as temporary:
        staged = Path(temporary)
        stage_help(root, staged, version)
        with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as bundle:
            for path in sorted(staged.rglob('*')):
                if path.is_file():
                    bundle.write(path, path.relative_to(staged).as_posix())
    return archive


def write_download_readme(output, version):
    (output / 'README.md').write_text(f'''# Gantry {version} downloads

Choose the installer for your operating system. It includes the GUI, command-line
launcher, Java runtime, offline getting-started guide, screenshots, SVG samples,
license and version information. You do not need to install Java separately.

## Windows (priority platform)

Install `Gantry-{version}-windows-x64.msi` for your Windows account. Open Gantry
from the Start menu. Open Gantry Help from the same menu, or choose Help >
Getting Started (Offline) in Gantry. Windows candidates are currently unsigned.

The default installation is `%LOCALAPPDATA%\\Gantry`; you can choose another
folder. `Gantry.exe` opens the GUI. `gantry-cli.exe --help` runs from a terminal
using the bundled runtime. The CLI has no desktop or Start menu shortcut and
installation does not modify PATH. Offline help and samples are in `app\\docs`.
Settings/history/recovery use `%APPDATA%\\Gantry` and survive uninstall.

## Ubuntu

Install `Gantry-{version}-linux-x64.deb` with:

```bash
sudo apt install ./Gantry-{version}-linux-x64.deb
```

Open Gantry from the applications menu. Native CLI: `/opt/gantry/bin/gantry-cli`.
Offline help: `/opt/gantry/lib/app/docs/index.html`. Profiles and saved work survive
uninstall. Other Linux distributions and architectures are not yet validated.

## Portable Java downloads

These are separate downloads for people with Java 17 or newer:

- `Gantry-{version}.jar`: GUI; run `java -jar Gantry-{version}.jar`.
- `Gantry-CLI-{version}.jar`: terminal; run `java -jar Gantry-CLI-{version}.jar --help`.
- `Gantry-{version}-docs.zip`: complete offline guide, screenshots and samples.
  Extract beside the GUI JAR and open `docs/index.html`.

Installer artifacts contain the platform installer and this standalone README.
The portable artifact contains both JARs and the documentation ZIP. No local
repository `docs/` folder is required to read this README; it contains no images
or relative documentation links. SHA256SUMS covers files in each artifact.

Start with guided mock practice before connecting hardware. Installer testing
and mock plots do not establish physical plotter calibration or compatibility.

[Release and acceptance notes](https://github.com/utrost/Gantry/releases/tag/v{version})
[Source and online guides](https://github.com/utrost/Gantry)
[Issue tracker](https://github.com/utrost/Gantry/issues)
''', encoding='utf-8')
