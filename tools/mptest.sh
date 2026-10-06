#!/bin/bash
# The multiplayer test: a dedicated server and three clients (Shooter, Victim, Netherling) playing one Ginnungagap
# event together (MultiplayerTestServer, MultiplayerTestClient). Needs the self-test world: build/selftest-server/selftest
# (./gradlew runGenworld). Screenshots land in build/mp/<client>/screenshots, logs in build/mp/*.log.
cd "$(dirname "$0")/.." || exit 1
rm -rf build/mp
mkdir -p build/mp/server build/mp/shooter build/mp/victim build/mp/netherling
cp -r build/selftest-server/selftest build/mp/server/world
rm -f build/mp/server/world/session.lock
echo "eula=true" > build/mp/server/eula.txt
printf "online-mode=false\nlevel-name=world\nspawn-protection=0\nallow-flight=true\nview-distance=10\nsimulation-distance=8\nmax-players=8\npvp=true\n" > build/mp/server/server.properties
for who in shooter victim netherling; do cp .github/selftest/options.txt build/mp/$who/options.txt; done
./gradlew compileGametestJava -q > /dev/null 2>&1
./gradlew runMpserver > build/mp/server.log 2>&1 &
SERVER=$!
for i in $(seq 1 120); do grep -q "\[mptest\] server ready" build/mp/server.log && break; sleep 2; done
echo "server up after ${i}x2s"
for who in shooter victim netherling; do
  ./gradlew runMp$who > build/mp/$who.log 2>&1 &
  sleep 25
done
wait $SERVER
echo "server exit"
sleep 5
grep -h "\[mptest\]\|was swallowed\|was erased\|Ginnungagap #" build/mp/server.log build/mp/*.log | grep -v "Render thread/INFO.*screenshot" | sed 's/^\[[0-9:]*\] //' | head -80
ls build/mp/*/screenshots 2>/dev/null
