import unittest
from release_metadata import metadata


class ReleaseMetadataTest(unittest.TestCase):
    def test_development_tag(self):
        self.assertEqual({'tag': 'v0.5.0-dev', 'prerelease': 'true'}, metadata('refs/tags/v0.5.0-dev', 'versionName = "0.5.0-dev"'))

    def test_stable_tag(self):
        self.assertEqual('false', metadata('refs/tags/v1.2.3', 'versionName = "1.2.3"')['prerelease'])

    def test_numbered_prerelease(self):
        self.assertEqual('true', metadata('refs/tags/v1.0.0-rc.1', 'versionName = "1.0.0-rc.1"')['prerelease'])

    def test_branch_rejected(self):
        with self.assertRaises(ValueError):
            metadata('refs/heads/main', 'versionName = "0.5.0-dev"')

    def test_mismatch_rejected(self):
        with self.assertRaises(ValueError):
            metadata('refs/tags/v0.6.0', 'versionName = "0.5.0-dev"')

    def test_unsafe_tags_rejected(self):
        for tag in ['v1.0.0;id', 'v1.0.0\nx=y', 'v01.0.0', 'v1.0.0/evil', 'v1.0.0-']:
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                metadata('refs/tags/' + tag, 'versionName = "' + tag[1:] + '"')
