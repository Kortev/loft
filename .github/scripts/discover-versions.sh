#!/usr/bin/env bash
# Dev helper: prints the Fabric toolchain versions available for 1.21.1 and pins
# the newest ones into gradle.properties for this CI run.
set -uo pipefail
mkdir -p ci-out
meta() { curl -fsS --retry 3 "$@"; }
{
	echo "== loader =="
	meta https://meta.fabricmc.net/v2/versions/loader | python3 -c 'import json,sys; [print(x["version"], "stable" if x["stable"] else "") for x in json.load(sys.stdin)[:8]]'
	echo "== yarn 1.21.1 =="
	meta https://meta.fabricmc.net/v2/versions/yarn/1.21.1 | python3 -c 'import json,sys; [print(x["version"]) for x in json.load(sys.stdin)[:5]]'
	echo "== fabric-api for 1.21.1 =="
	meta https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml | grep -o '<version>[^<]*+1\.21\.1</version>' | sed 's/<[^>]*>//g' | sort -V | tail -5
	echo "== loom =="
	meta https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml | grep -o '<version>[^<]*</version>' | sed 's/<[^>]*>//g' | grep -E '^1\.[0-9]+' | sort -V | tail -25
	echo "== game versions =="
	meta https://meta.fabricmc.net/v2/versions/game | python3 -c 'import json,sys; [print(x["version"], "stable" if x["stable"] else "") for x in json.load(sys.stdin)[:25]]'
} | tee ci-out/versions.txt

LOADER=$(meta https://meta.fabricmc.net/v2/versions/loader | python3 -c 'import json,sys; print(next(x["version"] for x in json.load(sys.stdin) if x["stable"]))')
YARN=$(meta https://meta.fabricmc.net/v2/versions/yarn/1.21.1 | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["version"])')
FAPI=$(meta https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml | grep -o '<version>[^<]*+1\.21\.1</version>' | sed 's/<[^>]*>//g' | sort -V | tail -1)
echo "Using loader=$LOADER yarn=$YARN fabric-api=$FAPI" | tee -a ci-out/versions.txt
sed -i "s/^loader_version=.*/loader_version=$LOADER/; s/^yarn_mappings=.*/yarn_mappings=$YARN/; s/^fabric_version=.*/fabric_version=$FAPI/" gradle.properties
