"""Checks the built jars, which the game tests never load (they run the mods from the dev build):

- each mod's jar holds its manifest, classes and assets, and nothing of the other mod's;
- every mixin config it lists names a refmap that is in the jar. Without one, mixins that worked in the dev build
  find nothing to hook in a real game and it crashes at start.

Usage: check-jars.py DIR (the folder with every mod's jar)."""
import glob
import json
import os
import sys
import zipfile

problems = []


def jar(folder, prefix):
    found = [p for p in glob.glob(os.path.join(folder, prefix + '-*.jar')) if not p.endswith('-sources.jar')]
    if len(found) != 1:
        problems.append('expected one %s jar in %s, found %s' % (prefix, folder, found))
        return None
    return found[0]


def check(path, mod_id, holds, never):
    with zipfile.ZipFile(path) as z:
        names = set(z.namelist())
        meta = json.loads(z.read('fabric.mod.json'))
        if meta.get('id') != mod_id:
            problems.append('%s: mod id is %r, not %r' % (path, meta.get('id'), mod_id))
        for entry in meta.get('mixins', []):
            config = entry if isinstance(entry, str) else entry['config']
            if config not in names:
                problems.append('%s: mixin config %s is missing' % (path, config))
                continue
            refmap = json.loads(z.read(config)).get('refmap')
            if not refmap or refmap not in names:
                problems.append('%s: mixin config %s has no refmap in the jar (refmap %r)' % (path, config, refmap))
        for name in holds:
            if name not in names:
                problems.append('%s: missing %s' % (path, name))
        for prefix in never:
            strays = sorted(n for n in names if n.startswith(prefix))
            if strays:
                problems.append('%s: holds what is not its own: %s' % (path, ', '.join(strays[:5])))
        print('%s: %s %s, %d entries' % (os.path.basename(path), meta.get('id'), meta.get('version'), len(names)))


folder = sys.argv[1]
star = jar(folder, 'shooting-star')
chitty = jar(folder, 'chitty-chitty-bang-bang')
if star:
    check(star, 'shootingstar', [
        'io/github/kortev/shootingstar/ShootingStar.class',
        'io/github/kortev/shootingstar/client/ShootingStarClient.class',
        'assets/shootingstar/lang/en_us.json',
        'assets/shootingstar/sounds.json',
        'data/shootingstar/advancement/gungnir/danger_close.json',
    ], [
        'io/github/kortev/chitty/',
        'assets/shootingstar/meshes/chitty',
        'assets/shootingstar/sounds/chitty_',
        'data/shootingstar/advancement/chitty/',
        'data/shootingstar/recipe/chitty',
        'assets/shootingstar/meshes/airship',
        'assets/shootingstar/sounds/airship_',
        'data/shootingstar/advancement/airship/',
        'data/shootingstar/recipe/airship',
        'assets/shootingstar/meshes/carriage',
        'assets/shootingstar/sounds/carriage_',
        'data/shootingstar/advancement/carriage/',
        'data/shootingstar/recipe/carriage',
    ])
if chitty:
    check(chitty, 'chitty', [
        'io/github/kortev/chitty/Chitty.class',
        'io/github/kortev/chitty/client/ChittyClient.class',
        'assets/shootingstar/meshes/chitty.cbm',
        'assets/shootingstar/textures/entity/chitty.png',
        'assets/shootingstar/textures/item/chitty.png',
        'assets/shootingstar/lang/en_us.json',
        'assets/shootingstar/sounds.json',
        'assets/shootingstar/sounds/chitty_engine_idle.ogg',
        'data/shootingstar/recipe/chitty.json',
        'data/shootingstar/advancement/chitty/root.json',
        'io/github/kortev/chitty/airship/AirshipEntity.class',
        'io/github/kortev/chitty/client/AirshipRenderer.class',
        'chitty.mixins.json',
        'assets/shootingstar/meshes/airship.cbm',
        'assets/shootingstar/textures/entity/airship.png',
        'assets/shootingstar/textures/item/airship.png',
        'assets/shootingstar/textures/item/airship_bomb.png',
        'assets/shootingstar/sounds/airship_engine.ogg',
        'data/shootingstar/recipe/airship.json',
        'data/shootingstar/recipe/airship_bomb.json',
        'data/shootingstar/advancement/airship/root.json',
        'io/github/kortev/chitty/carriage/CarriageEntity.class',
        'io/github/kortev/chitty/client/CarriageRenderer.class',
        'assets/shootingstar/textures/entity/carriage_harness.png',
        'assets/shootingstar/sounds/carriage_roll.ogg',
        'data/shootingstar/recipe/carriage.json',
        'data/shootingstar/advancement/carriage/root.json',
    ], [
        'io/github/kortev/shootingstar/',
        'assets/shootingstar/shaders/',
        'data/shootingstar/advancement/gungnir/',
    ])

if problems:
    print('\n'.join(problems))
    sys.exit(1)
print('both jars hold what they should')
