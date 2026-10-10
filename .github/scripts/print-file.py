"""Prints files into the job log as base64 blocks with their size and SHA-256, so they can be
recovered from the log alone. With --part K --parts N only the K-th of N slices of each file is
printed, so a big file can be split across several jobs whose logs stay small."""
import argparse
import base64
import hashlib
import os

parser = argparse.ArgumentParser()
parser.add_argument('--part', type=int, default=0)
parser.add_argument('--parts', type=int, default=1)
parser.add_argument('files', nargs='+')
args = parser.parse_args()

for path in args.files:
    if not os.path.exists(path):
        print('missing', path)
        continue
    data = open(path, 'rb').read()
    text = base64.b64encode(data).decode('ascii')
    lines = [text[i:i + 4000] for i in range(0, len(text), 4000)]
    per = -(-len(lines) // args.parts)
    chunk = lines[args.part * per:(args.part + 1) * per]
    print('=== PART %s %d %d %d %s ===' % (os.path.basename(path), args.part, args.parts, len(data),
                                          hashlib.sha256(data).hexdigest()))
    for line in chunk:
        print(line)
    print('=== END ===')
