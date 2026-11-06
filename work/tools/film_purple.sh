#!/bin/bash
# Films the Hollow Purple test clip (FilmPurple: -Pselftest=film-purple), 1080x1920, and mixes its sound in.
#   bash work/tools/film_purple.sh            the take, then build/film/purple.mp4 and its contact sheets
#   bash work/tools/film_purple.sh --mux      only mix the last capture again
#   bash work/tools/film_purple.sh --reuse    the scene the last full take kept (build/film/purple_world): no building, no waiting for the light
#   bash work/tools/film_purple.sh --quick    no scene (the plain as it is): the cast alone, in a minute or two
# Needs ffmpeg on the PATH (see work/briefs), Java 21, the self-test world (python tools/film.py ... makes it).
set -e
cd "$(dirname "$0")/../.."
ROOT=$(pwd)
OUT="$ROOT/build/film"
CAPTURE="$ROOT/build/selftest/capture"
mkdir -p "$OUT"

EXTRA=""
[ "$1" = "--quick" ] && EXTRA="-Dshootingstar.film.quick=true"
WORLD="$ROOT/build/selftest-server/selftest"
if [ "$1" = "--reuse" ]; then
  WORLD="$OUT/purple_world"
  EXTRA="-Dshootingstar.film.reuse=true -Dshootingstar.film.spot=$(cygpath -m "$OUT/purple_spot.txt" 2>/dev/null || echo "$OUT/purple_spot.txt")"
fi
if [ "$1" != "--mux" ]; then
  rm -rf "$ROOT/build/selftest/saves/selftest" "$CAPTURE" "$ROOT/build/selftest/screenshots"
  mkdir -p "$ROOT/build/selftest/saves" "$ROOT/build/selftest/skins"
  cp -r "$WORLD" "$ROOT/build/selftest/saves/selftest"
  rm -f "$ROOT/build/selftest/saves/selftest/session.lock"
  cp "$ROOT/.github/selftest/options.txt" "$ROOT/build/selftest/options.txt"
  [ -f "$ROOT/build/selftest/skins/Rival.png" ] || python tools/film_skin.py "$ROOT/build/selftest/skins/Rival.png"
  ./gradlew runSelftest --offline -Pselftest=film-purple -Pwidth=540 -Pheight=960 -Pusername=Rival \
    "-Pjvmargs=-Xmx2600m -XX:TieredStopAtLevel=1 $EXTRA" || true
fi

if [ ! -f "$CAPTURE/video.mp4" ]; then
  echo "no video was recorded: see build/selftest/logs/latest.log"
  exit 1
fi
python work/tools/mux-audio-noprobe.py "$CAPTURE" "$OUT/purple_raw.mp4"
echo done
