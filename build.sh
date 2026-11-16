#!/bin/sh
# Build Update Compat Patches. Needs a JDK 25 and the game's jar + ZombieBuddy.jar (compile-time only, never bundled).
#   PZ=<game folder> ZB=<ZombieBuddy.jar> ./build.sh   -> UpdateCompatPatches.jar
set -e
PZ=${PZ:?set PZ to the Project Zomboid folder}; ZB=${ZB:-$PZ/ZombieBuddy.jar}
rm -rf out && mkdir -p out
javac --release 25 -encoding UTF-8 -cp "$PZ/projectzomboid.jar:$ZB" -d out src/jay/compat/*.java
cp src/jay/compat/compat-rules.txt out/jay/compat/
printf 'Manifest-Version: 1.0\nImplementation-Title: Update Compat Patches\n' > out/manifest.txt
(cd out && jar cfm ../UpdateCompatPatches.jar manifest.txt jay)
echo built UpdateCompatPatches.jar
