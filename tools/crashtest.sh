#!/bin/bash
# The server going down without warning in the middle of a Ginnungagap event, and coming back (CrashTestServer): a
# dedicated server on a copy of the self-test world (./gradlew runGenworld) is killed outright partway through the
# carving of the hole, just after a save, then started again and checked. Logs in build/crash/*.log.
cd "$(dirname "$0")/.." || exit 1
rm -rf build/crash
mkdir -p build/crash/server
cp -r build/selftest-server/selftest build/crash/server/world
rm -f build/crash/server/world/session.lock
echo "eula=true" > build/crash/server/eula.txt
printf "online-mode=false\nlevel-name=world\nspawn-protection=0\nview-distance=8\nsimulation-distance=8\n" > build/crash/server/server.properties
./gradlew runCrashserver -Pcrashtest=crash > build/crash/crash.log 2>&1
echo "crash phase exit $?"
./gradlew runCrashserver -Pcrashtest=recover > build/crash/recover.log 2>&1
echo "recover phase exit $?"
grep -h "\[crashtest\]\|Ginnungagap\|cut short\|spawn was in" build/crash/crash.log build/crash/recover.log | sed 's/^\[[0-9:]*\] \[[^]]*\] //'
