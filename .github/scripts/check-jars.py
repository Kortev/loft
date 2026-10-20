"""Checks the built jars, which the game tests never load (they run the mods from the dev build):

- each mod's jar holds its manifest, classes and assets, and nothing of the other mods';
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
gojolander = jar(folder, 'gojolander')
chainsaw = jar(folder, 'chainsawman')
if star:
    check(star, 'shootingstar', [
        'io/github/kortev/shootingstar/ShootingStar.class',
        'io/github/kortev/shootingstar/client/ShootingStarClient.class',
        'assets/shootingstar/lang/en_us.json',
        'assets/shootingstar/sounds.json',
        'data/shootingstar/advancement/gungnir/danger_close.json',
        'assets/shootingstar/meshes/mjolnir.hbm',
        'assets/shootingstar/textures/entity/mjolnir_baked.png',
        'assets/shootingstar/textures/entity/mjolnir_glow.png',
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
        'io/github/kortev/gojolander/',
        'assets/gojolander/',
        'data/gojolander/',
        'io/github/kortev/chainsawman/',
        'assets/chainsawman/',
        'data/chainsawman/',
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
    ], [
        'io/github/kortev/shootingstar/',
        'assets/shootingstar/shaders/',
        'data/shootingstar/advancement/gungnir/',
        'io/github/kortev/gojolander/',
        'assets/gojolander/',
        'data/gojolander/',
        'io/github/kortev/chainsawman/',
        'assets/chainsawman/',
        'data/chainsawman/',
    ])
if gojolander:
    check(gojolander, 'gojolander', [
        'io/github/kortev/gojolander/Gojolander.class',
        'io/github/kortev/gojolander/client/GojolanderClient.class',
        'io/github/kortev/gojolander/domain/DomainManager.class',
        'gojolander.mixins.json',
        'gojolander.client.mixins.json',
        'assets/gojolander/lang/en_us.json',
        'assets/gojolander/sounds.json',
        'assets/gojolander/sounds/domain_void.ogg',
        'assets/gojolander/shaders/core/gl_domain.json',
        'assets/gojolander/shaders/core/gl_domain.fsh',
        'assets/gojolander/shaders/core/gl_flood.json',
        'assets/gojolander/textures/item/blindfold.png',
        'assets/gojolander/textures/entity/blindfold.png',
        'assets/gojolander/textures/entity/six_eyes.png',
        'data/gojolander/recipe/blindfold.json',
        'data/gojolander/advancement/root.json',
    ], [
        'io/github/kortev/shootingstar/',
        'io/github/kortev/chitty/',
        'io/github/kortev/chainsawman/',
        'assets/shootingstar/',
        'data/shootingstar/',
        'assets/chainsawman/',
        'data/chainsawman/',
    ])
if chainsaw:
    check(chainsaw, 'chainsawman', [
        'io/github/kortev/chainsawman/ChainsawMan.class',
        'io/github/kortev/chainsawman/client/ChainsawManClient.class',
        'assets/chainsawman/lang/en_us.json',
        'io/github/kortev/chainsawman/PochitaEntity.class',
        'assets/chainsawman/textures/entity/pochita.png',
        'chainsawman.mixins.json',
        'chainsawman.client.mixins.json',
        'assets/chainsawman/meshes/form.csm',
        'assets/chainsawman/textures/entity/form.png',
    ], [
        'io/github/kortev/shootingstar/',
        'io/github/kortev/chitty/',
        'io/github/kortev/gojolander/',
        'assets/shootingstar/',
        'data/shootingstar/',
        'assets/gojolander/',
        'data/gojolander/',
    ])

if problems:
    print('\n'.join(problems))
    sys.exit(1)
print('every jar holds what it should')
