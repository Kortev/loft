"""Records the Genesis Key Short from one player's eyes on this computer (FilmSelfTest), ready to edit.

    python tools/film.py kortev            # or woomerd, gerald; several at once; or all
    python tools/film.py all --fresh-world  # make the test world again first
    python tools/film.py domain             # the first friend's ult, from his eyes (FilmDomain), Kortev caught in it

Each take starts from a clean copy of the self-test world (made once with runGenworld, seed "shootingstar"), plays the
whole scripted episode with the other two players moved by the server, and writes:

    build/film/<who>.mp4          the master: 1080x1920, 30 fps, the game's sounds mixed in (edit from this)
    build/film/<who>-stills/      screenshots at the scene's beats, to look it over without the video
    build/film/moves/<who>.tsv    how they moved, tick by tick: the other two's takes play it back on the server's copy
    build/film/<who>-game.wav     the master's sound without Minecraft's own music (for a Short with music of its own)
    build/film/<who>-capture/     the take's raw video and sounds, to mix again from

So that the others move as they did in their own takes, everyone is recorded at least once before the takes that
matter: "all" records Kortev, Woomerd and gerald, then Kortev and Woomerd again if anyone's moves were missing.

The game is drawn at 1080x1920 whatever the window's size; the window (540x960 by default, --window WxH) only shows
it. The game is silent while it records (its sounds are logged and mixed in afterwards) and ignores the keyboard and
mouse, so the computer can be used meanwhile. A take is about two minutes of footage; how long it takes to make
depends on the graphics card.

Needs: Java 21 (JAVA_HOME or on the PATH), ffmpeg on the PATH, Python 3 with numpy. Kortev's and Woomerd's skins are
fetched from Mojang the first time (build/selftest/skins; --skins fetches them again).
"""
import argparse
import json
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BUILD = os.path.join(ROOT, 'build')
SERVER = os.path.join(BUILD, 'selftest-server')
CLIENT = os.path.join(BUILD, 'selftest')
OUT = os.path.join(BUILD, 'film')
SCRIPTS = os.path.join(ROOT, '.github', 'scripts')
WHO = {'kortev': 'Kortev', 'woomerd': 'Woomerd', 'gerald': 'gerald_2009', 'domain': 'Rival'}
# The Genesis Key series' three takes ("all"); "domain" is a film of its own.
SERIES = ['kortev', 'woomerd', 'gerald']
GRADLEW = os.path.join(ROOT, 'gradlew.bat' if os.name == 'nt' else 'gradlew')


def say(*words):
    print('[film]', *words, flush=True)


def check():
    missing = []
    if not shutil.which('ffmpeg'):
        missing.append('ffmpeg (on the PATH)')
    java = os.path.join(os.environ['JAVA_HOME'], 'bin', 'java') if os.environ.get('JAVA_HOME') else shutil.which('java')
    if not java:
        missing.append('Java 21 (JAVA_HOME or on the PATH)')
    else:
        version = subprocess.run([java, '-version'], capture_output=True, text=True).stderr
        if '"21' not in version and ' 21' not in version:
            say('warning: Java 21 is wanted, this is:', version.splitlines()[0] if version else '?')
    try:
        import numpy  # noqa: F401
    except ImportError:
        missing.append('numpy (pip install numpy)')
    if missing:
        say('missing:', ', '.join(missing))
        sys.exit(1)


def gradle(*args, check=True):
    say('gradlew', ' '.join(args))
    return subprocess.run([GRADLEW, *args, '--stacktrace'], cwd=ROOT, check=check).returncode


def world(fresh):
    level = os.path.join(SERVER, 'selftest')
    if fresh and os.path.isdir(level):
        shutil.rmtree(level)
    if os.path.isdir(level):
        return
    say('making the self-test world')
    os.makedirs(SERVER, exist_ok=True)
    with open(os.path.join(SERVER, 'eula.txt'), 'w') as f:
        f.write('eula=true\n')
    with open(os.path.join(SERVER, 'server.properties'), 'w') as f:
        f.write('level-name=selftest\nlevel-seed=shootingstar\nonline-mode=false\nspawn-protection=0\n')
    gradle('runGenworld')


def skins(again):
    folder = os.path.join(CLIENT, 'skins')
    # The first friend's stand-in (his own skin goes here instead, once there is his: Rival.png).
    rival = os.path.join(folder, 'Rival.png')
    if not os.path.exists(rival):
        os.makedirs(folder, exist_ok=True)
        subprocess.run([sys.executable, os.path.join(ROOT, 'tools', 'film_skin.py'), rival], check=True)
    if not again and all(os.path.exists(os.path.join(folder, n + '.png')) for n in ('Kortev', 'Woomerd')):
        return
    say('fetching skins')
    subprocess.run([sys.executable, os.path.join(SCRIPTS, 'fetch-skins.py'), folder, 'Kortev', 'Woomerd'])


def take(who, window):
    name = WHO[who]
    say('take:', name)
    # A clean world and options every take: the last one left a hole in it.
    saves = os.path.join(CLIENT, 'saves', 'selftest')
    shutil.rmtree(saves, ignore_errors=True)
    shutil.copytree(os.path.join(SERVER, 'selftest'), saves)
    lock = os.path.join(saves, 'session.lock')
    if os.path.exists(lock):
        os.remove(lock)
    shutil.copy(os.path.join(ROOT, '.github', 'selftest', 'options.txt'), os.path.join(CLIENT, 'options.txt'))
    capture = os.path.join(CLIENT, 'capture')
    shots = os.path.join(CLIENT, 'screenshots')
    shutil.rmtree(capture, ignore_errors=True)
    shutil.rmtree(shots, ignore_errors=True)
    width, height = window.lower().split('x')
    # The game can hang on its way down after the take, and is then stopped (FilmSelfTest): the take is all written by
    # then, so what counts is whether its video is there and finished, not how the game ended.
    code = gradle('runSelftest', '-Pselftest=film-' + who, '-Pwidth=' + width, '-Pheight=' + height, '-Pusername=' + name,
                  check=False)
    log = os.path.join(CLIENT, 'logs', 'latest.log')
    finished = os.path.exists(log) and '[capture] ffmpeg finished: exit 0' in open(log, encoding='utf-8', errors='replace').read()
    if code != 0 and not finished:
        say('the game failed before the take was finished: see build/selftest/logs/latest.log')
        sys.exit(1)

    os.makedirs(OUT, exist_ok=True)
    if not os.path.exists(os.path.join(capture, 'video.mp4')):
        say('no video was recorded: see build/selftest/logs/latest.log')
        sys.exit(1)
    # A loop the take tracked itself (loops.json: the Domain's hum) is heard as its track, not once from its start too.
    loops = os.path.join(capture, 'loops.json')
    log = os.path.join(capture, 'sounds.json')
    if os.path.exists(loops) and os.path.exists(log):
        tracked = {t['loop'] for t in json.load(open(loops))['tracks']}
        with open(log) as f:
            sounds = json.load(f)
        with open(log, 'w') as f:
            json.dump([x for x in sounds if x['file'] not in tracked], f, indent=1)
    subprocess.run([sys.executable, os.path.join(SCRIPTS, 'render-loops.py'), capture])
    master = os.path.join(OUT, who + '.mp4')
    env = dict(os.environ, MUX_CRF='17', MUX_PRESET='medium')
    subprocess.run([sys.executable, os.path.join(SCRIPTS, 'mux-audio.py'), capture, master], env=env, check=True)
    # The game's sound without Minecraft's own music, for a Short with music of its own (shorts.py "audio"), and the
    # capture itself, kept, to mix again from.
    game = os.path.join(OUT, who + '-game.wav')
    subprocess.run([sys.executable, os.path.join(SCRIPTS, 'mux-audio.py'), capture, game],
                   env=dict(os.environ, MUX_EXCLUDE='minecraft_sounds_music'), check=True)
    kept = os.path.join(OUT, who + '-capture')
    shutil.rmtree(kept, ignore_errors=True)
    shutil.copytree(capture, kept)
    stills = os.path.join(OUT, who + '-stills')
    shutil.rmtree(stills, ignore_errors=True)
    if os.path.isdir(shots):
        shutil.copytree(shots, stills)
    say('done:', master)


def main():
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('who', nargs='+', choices=[*WHO, 'all'], help='whose eyes the take is from')
    parser.add_argument('--window', default='540x960', help='the window size (the film is 1080x1920 whatever this is)')
    parser.add_argument('--fresh-world', action='store_true', help='make the self-test world again first')
    parser.add_argument('--skins', action='store_true', help="fetch Kortev's and Woomerd's skins again")
    args = parser.parse_args()
    who = list(SERIES) if 'all' in args.who else list(dict.fromkeys(args.who))
    if 'all' in args.who and not all(os.path.exists(os.path.join(OUT, 'moves', w + '.tsv')) for w in SERIES):
        who += ['kortev', 'woomerd']
    check()
    world(args.fresh_world)
    skins(args.skins)
    for w in who:
        take(w, args.window)
    say('all done:', ', '.join(os.path.join('build', 'film', w + '.mp4') for w in dict.fromkeys(who)))


if __name__ == '__main__':
    main()
