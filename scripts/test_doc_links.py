import tempfile
import unittest
from pathlib import Path
from check_doc_links import check


class DocumentationLinksTest(unittest.TestCase):
    def test_local_files_images_and_duplicate_headings(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "image one.png").write_bytes(b"image")
            target = root / "guide.md"
            target.write_text("# Hello `world`\n# Again\n# Again\n", encoding="utf-8")
            source = root / "README.md"
            source.write_text("[Guide](guide.md#hello-world)\n[Duplicate](guide.md#again-1)\n![Image](<image%20one.png>)\n", encoding="utf-8")
            count, errors = check([source])
            self.assertEqual((3, []), (count, errors))

    def test_reports_missing_file_and_anchor(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "README.md"
            source.write_text("# Start\n[Broken](missing.md)\n[Bad anchor](#missing)\n", encoding="utf-8")
            count, errors = check([source])
            self.assertEqual(2, count)
            self.assertEqual(2, len(errors))
            self.assertIn("missing local target", errors[0])
            self.assertIn("missing heading anchor", errors[1])

    def test_ignores_fenced_examples_and_external_links(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "README.md"
            source.write_text("```md\n[Example](missing.md)\n```\n[Web](https://example.com/#anything)\n", encoding="utf-8")
            self.assertEqual((0, []), check([source]))
