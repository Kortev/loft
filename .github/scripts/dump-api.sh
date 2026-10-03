#!/usr/bin/env bash
# Dev helper: dumps member signatures of the Yarn-mapped Minecraft and Fabric API
# classes (uploaded as an artifact), and prints the classes listed in
# .github/scripts/api-classes.txt to the job log.
set -uo pipefail
OUT=ci-out/api
mkdir -p "$OUT"
MC_JARS=$(find .gradle ~/.gradle/caches/fabric-loom -path '*minecraftMaven*' -name 'minecraft-*.jar' ! -name '*-sources.jar' 2>/dev/null)
FAPI_JARS=$(find .gradle -path '*remapped_mods*' -name '*.jar' ! -name '*-sources.jar' 2>/dev/null)
printf '%s\n' $MC_JARS > "$OUT/mc-jars.txt"
printf '%s\n' $FAPI_JARS > "$OUT/fapi-jars.txt"
CP=$(printf '%s:' $MC_JARS $FAPI_JARS)
list_classes() { for j in "$@"; do unzip -Z1 "$j" | grep '\.class$' | grep -v '\$[0-9]' | sed 's/\.class$//; s#/#.#g'; done | sort -u; }
list_classes $MC_JARS > "$OUT/mc-classes.txt"
list_classes $FAPI_JARS > "$OUT/fapi-classes.txt"
grep -E '^(net\.minecraft|com\.mojang\.blaze3d)' "$OUT/mc-classes.txt" | xargs -d '\n' -n 300 javap -p -constants -cp "$CP" > "$OUT/mc-javap.txt" 2>&1
grep '\.api\.' "$OUT/fapi-classes.txt" | xargs -d '\n' -n 300 javap -p -constants -cp "$CP" > "$OUT/fapi-javap.txt" 2>&1
gzip -9kf "$OUT/mc-javap.txt" "$OUT/fapi-javap.txt"
ls -la "$OUT"
if [ -f .github/scripts/api-classes.txt ]; then
	echo "=== API SIGNATURES ==="
	grep -v '^#' .github/scripts/api-classes.txt | grep . | xargs -d '\n' javap -p -constants -cp "$CP" 2>&1 | grep -v 'lambda\$' | grep -v 'static {};'
	echo "=== END API SIGNATURES ==="
fi
