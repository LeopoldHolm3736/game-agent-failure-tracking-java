#!/usr/bin/env sh
set -eu
classes="${TMPDIR:-/tmp}/game-agent-failure-classes"
rm -rf "$classes"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java -name '*.java' -print)
java -cp "$classes" cc.infrai.game.GameBackendFailureExample
