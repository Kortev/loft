"""Prints the self test's screenshots into the job log as small base64 JPEGs, so they can be
inspected from the log alone."""
import base64
import io
import os
import sys

from PIL import Image

folder = sys.argv[1] if len(sys.argv) > 1 else 'build/selftest/screenshots'
if not os.path.isdir(folder):
    print('no screenshots in', folder)
    sys.exit(0)
for name in sorted(os.listdir(folder)):
    if not name.endswith('.png'):
        continue
    image = Image.open(os.path.join(folder, name)).convert('RGB')
    image.thumbnail((640, 360))
    buffer = io.BytesIO()
    image.save(buffer, 'JPEG', quality=72)
    data = base64.b64encode(buffer.getvalue()).decode('ascii')
    print('=== SCREEN %s ===' % name)
    for i in range(0, len(data), 4000):
        print(data[i:i + 4000])
    print('=== END ===')
