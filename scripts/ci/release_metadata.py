"""Validate a release tag against the Android version before using any secrets."""
import os
import re
from pathlib import Path

TAG = re.compile(r'v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([a-zA-Z0-9]+(?:[.-][a-zA-Z0-9]+)*))?')


def metadata(ref, gradle):
    if not ref.startswith('refs/tags/'):
        raise ValueError('Release must run on an existing version tag, not a branch.')
    tag = ref.removeprefix('refs/tags/')
    match = TAG.fullmatch(tag)
    if not match:
        raise ValueError('Expected a tag such as v0.5.0-dev or v0.5.0.')
    version = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
    if not version or version.group(1) != tag[1:]:
        raise ValueError('Tag must match versionName in app/build.gradle.kts. Update versionName and versionCode before tagging.')
    return {'tag': tag, 'prerelease': str(match.group(4) is not None).lower()}


if __name__ == '__main__':
    values = metadata(os.environ['GITHUB_REF'], Path('app/build.gradle.kts').read_text())
    with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
        for name, value in values.items():
            print(f'{name}={value}', file=output)
