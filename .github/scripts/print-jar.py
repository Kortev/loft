"""Dev helper: prints the built mod jar into the job log as base64 so it can be fetched from the
log alone."""
import base64
import glob
import hashlib

for path in sorted(glob.glob('build/libs/*.jar')):
    if path.endswith('-sources.jar'):
        continue
    data = open(path, 'rb').read()
    print('=== JAR %s %d %s ===' % (path.split('/')[-1], len(data), hashlib.sha256(data).hexdigest()))
    encoded = base64.b64encode(data).decode('ascii')
    for i in range(0, len(encoded), 4000):
        print(encoded[i:i + 4000])
    print('=== END ===')
