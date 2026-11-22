import hashlib
import json
import os
from pathlib import Path

out = Path('/tmp/cimanow-output')
repo = os.environ['GITHUB_REPOSITORY']
base = f'https://raw.githubusercontent.com/{repo}/cimanow-test-builds'
plugins = json.loads(Path('build/plugins.json').read_text())
plugins = [p for p in plugins if p['internalName'] == 'CimaNowBrowserTest']
assert len(plugins) == 1, 'Only the isolated plugin may be published'
artifact = out / 'CimaNowBrowserTest.cs3'
plugins[0].update(repositoryUrl=f'https://github.com/{repo}', url=f'{base}/CimaNowBrowserTest.cs3', name='CimaNow Browser Test',
                  fileSize=artifact.stat().st_size,
                  fileHash='sha256-' + hashlib.sha256(artifact.read_bytes()).hexdigest())
(out / 'plugins.json').write_text(json.dumps(plugins, ensure_ascii=False, indent=2))
(out / 'repo.json').write_text(json.dumps({
    'name': 'Kemo CimaNow Test Only',
    'description': 'Isolated experimental browser bridge. Stable ExampleProvider unchanged.',
    'manifestVersion': 1,
    'pluginLists': [f'{base}/plugins.json']
}, ensure_ascii=False, indent=2))
(out / 'README.md').write_text(Path('CimaNowBrowserTest/README.md').read_text())
(out / 'SHA256SUMS').write_text(hashlib.sha256(artifact.read_bytes()).hexdigest() + '  CimaNowBrowserTest.cs3\n')
