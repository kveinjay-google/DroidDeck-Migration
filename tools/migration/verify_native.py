from pathlib import Path
import json, hashlib
p = Path(__file__).resolve().parents[2]
manifest = json.loads((p / 'tools/migration/native-origin.json').read_text())
for name, expected in manifest['members'].items():
    actual = hashlib.sha256((p / name).read_bytes()).hexdigest()
    if actual != expected: raise SystemExit('Native provenance mismatch: ' + name)
print('Verified', len(manifest['members']), 'exact-upstream native inputs')

assets = json.loads((p / 'tools/migration/runtime-payload-origin.json').read_text())
root = Path('/Volumes/LEXA 4TB DISK/BuildOutputs/droiddeck-migration/gradle/app/runtimeAssets')
for name, expected in assets.items():
    actual = hashlib.sha256((root / name).read_bytes()).hexdigest()
    if actual != expected: raise SystemExit('Runtime asset mismatch: ' + name)
print('Verified', len(assets), 'runtime assets (migration socket shim deliberately updated)')
