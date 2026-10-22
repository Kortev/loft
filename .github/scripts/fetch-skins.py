"""Fetches players' current skins from Mojang for the film self tests: python3 fetch-skins.py <folder> <name>...
Writes <name>.png and <name>.model ("slim" or "default") for each player found; anyone not found keeps a default skin."""
import base64
import json
import os
import sys
import urllib.request

folder, names = sys.argv[1], sys.argv[2:]
os.makedirs(folder, exist_ok=True)


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'shootingstar-selftest'}), timeout=20) as r:
        return r.read()


for name in names:
    try:
        profile = json.loads(get('https://api.mojang.com/users/profiles/minecraft/' + name))
        session = json.loads(get('https://sessionserver.mojang.com/session/minecraft/profile/' + profile['id']))
        textures = next(p for p in session['properties'] if p['name'] == 'textures')
        skin = json.loads(base64.b64decode(textures['value']))['textures']['SKIN']
        with open(os.path.join(folder, name + '.png'), 'wb') as f:
            f.write(get(skin['url']))
        model = skin.get('metadata', {}).get('model', 'default')
        with open(os.path.join(folder, name + '.model'), 'w') as f:
            f.write(model)
        print(name, profile['name'], model)
    except Exception as e:  # noqa: BLE001 - any failure just leaves the default skin
        print('no skin for', name, '-', e)
