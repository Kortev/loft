"""Prints files into the job log as base64 blocks with their size and SHA-256, so they can be
recovered from the log alone."""
import base64
import hashlib
import os
import sys

for path in sys.argv[1:]:
    if not os.path.exists(path):
        print('missing', path)
        continue
    data = open(path, 'rb').read()
    print('=== FILE %s %d %s ===' % (os.path.basename(path), len(data), hashlib.sha256(data).hexdigest()))
    text = base64.b64encode(data).decode('ascii')
    for i in range(0, len(text), 4000):
        print(text[i:i + 4000])
    print('=== END ===')
