"""Verify the isolated CS3 package and report bounded test evidence, not playback."""
import hashlib
import json
import struct
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parents[1]
build = root / 'CimaNowBrowserTest/build'
artifact = build / 'CimaNowBrowserTest.cs3'
with ZipFile(artifact) as package:
    assert set(package.namelist()) == {'manifest.json', 'classes.dex'}
    manifest = json.loads(package.read('manifest.json'))
    dex = package.read('classes.dex')
assert manifest['version'] == 2
assert manifest['pluginClassName'] == 'com.kemo.cimanowtest.CimaNowBrowserTestPlugin'
assert manifest['requiresResources'] is False
assert dex.startswith(b'dex\n')
u32 = lambda offset: struct.unpack_from('<I', dex, offset)[0]
string_count, string_offset = u32(56), u32(60)
type_count, type_offset = u32(64), u32(68)
class_count, class_offset = u32(96), u32(100)

def string(index):
    assert index < string_count
    start = u32(string_offset + index * 4)
    while dex[start] & 0x80:
        start += 1
    start += 1
    return dex[start:dex.index(b'\x00', start)].decode('utf-8')

def descriptor(index):
    assert index < type_count
    return string(u32(type_offset + index * 4))

classes = {descriptor(u32(class_offset + index * 32)) for index in range(class_count)}
expected = set()
for folder in [build / 'tmp/kotlin-classes/debug', build / 'intermediates/javac/debug/compileDebugJavaWithJavac/classes']:
    for file in folder.rglob('com/kemo/cimanowtest/*.class'):
        relative = file.as_posix().split('/com/kemo/cimanowtest/', 1)[1]
        expected.add('Lcom/kemo/cimanowtest/' + relative[:-6] + ';')
assert expected, 'No compiled classes found'
assert expected <= classes, 'Compiled classes missing from DEX: ' + str(sorted(expected - classes))
assert not any('robolectric' in name.lower() or 'LayoutTest' in name or 'SignaturesTest' in name for name in classes)
for name in ['BrowserBridge', 'BrowserBridge$Media', 'BrowserBridge$Listener', 'MediaSignatures', 'CimaNowBrowserTestPlugin', 'CimaNowBrowserTestProvider']:
    assert 'Lcom/kemo/cimanowtest/' + name + ';' in classes
suites = []
for file in sorted((build / 'test-results/testDebugUnitTest').glob('TEST-*.xml')):
    suite = ET.parse(file).getroot()
    result = {key: suite.attrib[key] for key in ['name', 'tests', 'failures', 'errors', 'skipped']}
    assert int(result['failures']) == int(result['errors']) == int(result['skipped']) == 0
    suites.append(result)
assert sum(int(suite['tests']) for suite in suites) == 13
report = {
    'version': 2,
    'source_branch': 'cimanow-browser-test',
    'source_commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
    'artifact_branch': 'cimanow-test-builds',
    'artifact_bytes': artifact.stat().st_size,
    'artifact_sha256': hashlib.sha256(artifact.read_bytes()).hexdigest(),
    'manifest': manifest,
    'dex_class_count': class_count,
    'compiled_classes_confirmed_in_dex': len(expected),
    'test_suites': suites,
    'layout_evidence': {
        'framework': 'Robolectric, Android API 28 and 33',
        'webview': 'Test-only measurement/frame shadow, no Chromium and no website network',
        'images': 'Annotated native-control layout previews; the measured browser slot is shaded, not website-rendering evidence',
        'landscape_800x360_no_candidates': [800, 313],
        'landscape_800x360_one_fixture_candidate': [800, 257],
        'landscape_640x270_no_candidates': [640, 223],
    },
    'verified': ['Full Gradle build', 'Five media-signature tests', 'Four UI/diagnostic/cleanup tests on each of API 28 and 33', 'Packaged DEX definitions', 'Test classes excluded'],
    'not_verified': ['Real WebView rendering on a phone for v2', 'Website gateway acceptance', 'Watch-page/server flow', 'Correct-title media extraction', 'Native-player playback'],
    'stable': {'master': 'f37be47a7686bf908043d4f9c89506a2946a1a06', 'builds': '54550efeb01db6da33edc9a746d73236d7746875', 'ExampleProvider': 25},
    'classes': sorted(classes),
}
out = Path('/tmp/cimanow-output')
out.mkdir(exist_ok=True)
(out / 'verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
print(json.dumps({key: report[key] for key in ['version','artifact_bytes','artifact_sha256','dex_class_count','compiled_classes_confirmed_in_dex','test_suites']}, indent=2))
