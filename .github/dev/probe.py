"""Dev helper: prints decompiled Minecraft/Fabric sources and vanilla shader assets into the job log."""
import glob
import os
import re
import sys
import urllib.request
import zipfile

home = os.path.expanduser('~')
jars = sorted(set(glob.glob(home + '/.gradle/caches/fabric-loom/**/*-sources.jar', recursive=True)
                  + glob.glob('.gradle/loom-cache/**/*-sources.jar', recursive=True)))
print('=== source jars ===')
for j in jars:
    print(j)

# Fabric API module sources are not downloaded by genSources; fetch the rendering module directly.
for module in ['fabric-rendering-v1', 'fabric-lifecycle-events-v1']:
    dirs = glob.glob(home + '/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/%s/*' % module)
    for d in dirs:
        version = os.path.basename(d)
        url = 'https://maven.fabricmc.net/net/fabricmc/fabric-api/%s/%s/%s-%s-sources.jar' % (module, version, module, version)
        dest = '/tmp/%s-%s-sources.jar' % (module, version)
        try:
            urllib.request.urlretrieve(url, dest)
            jars.append(dest)
            print('fetched', url)
        except Exception as e:
            print('could not fetch', url, e)

index = {}
for j in jars:
    try:
        with zipfile.ZipFile(j) as z:
            for n in z.namelist():
                index.setdefault(n, j)
    except Exception as e:
        print('bad jar', j, e)


def show(path, regex=None, context=0):
    jar = index.get(path)
    if jar is None:
        cands = [n for n in index if n.endswith('/' + path.split('/')[-1])]
        print('=== MISSING %s (similar: %s) ===' % (path, cands[:5]))
        return
    text = zipfile.ZipFile(jar).read(path).decode('utf-8', 'replace').splitlines()
    print('=== SOURCE %s (%d lines) ===' % (path, len(text)))
    if regex is None:
        for i, line in enumerate(text, 1):
            print('%5d %s' % (i, line))
        return
    pat = re.compile(regex)
    keep = set()
    for i, line in enumerate(text):
        if pat.search(line):
            keep.update(range(max(0, i - context), min(len(text), i + context + 1)))
    last = -2
    for i in sorted(keep):
        if i != last + 1:
            print('  ...')
        print('%5d %s' % (i + 1, text[i]))
        last = i


for spec in open('.github/dev/probe-sources.txt'):
    spec = spec.strip()
    if not spec or spec.startswith('#'):
        continue
    parts = [p.strip() for p in spec.split('::')]
    show(parts[0], parts[1] if len(parts) > 1 else None, int(parts[2]) if len(parts) > 2 else 0)

for path in ['net/fabricmc/fabric/api/client/rendering/v1/CoreShaderRegistrationCallback.java',
             'net/fabricmc/fabric/api/client/rendering/v1/WorldRenderContext.java',
             'net/fabricmc/fabric/api/client/rendering/v1/WorldRenderEvents.java',
             'net/fabricmc/fabric/api/client/rendering/v1/HudRenderCallback.java']:
    show(path, r'(public|static|void|interface|Event<).*', 0)

client = glob.glob(home + '/.gradle/caches/fabric-loom/**/minecraft-client.jar', recursive=True)
print('=== client jars', client, '===')
if client:
    z = zipfile.ZipFile(client[0])
    names = z.namelist()
    print('=== core shaders ===')
    print(' '.join(sorted(os.path.basename(n) for n in names if n.startswith('assets/minecraft/shaders/core/') and n.endswith('.json'))))
    for spec in open('.github/dev/probe-assets.txt'):
        p = spec.strip()
        if p:
            print('=== ASSET %s ===' % p)
            print(z.read(p).decode('utf-8', 'replace') if p in names else 'MISSING')
