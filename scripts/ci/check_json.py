"""Dependency-free syntax check for the SDK's distributed JSON contracts."""
import json
from pathlib import Path

for path in sorted(Path('sdk').rglob('*.json')):
    json.loads(path.read_text(encoding='utf-8'))
    print(f'Valid JSON: {path}')
