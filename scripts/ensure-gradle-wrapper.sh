#!/usr/bin/env bash
# Gera o Gradle Wrapper (gradlew + gradle-wrapper.jar) sem versionar o .jar binário.
# Roda num diretório temporário para funcionar com qualquer Gradle instalado na máquina.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="$(tr -d '[:space:]' < "$ROOT/gradle/wrapper-version.txt")"

if [ -x "$ROOT/gradlew" ] && [ -f "$ROOT/gradle/wrapper/gradle-wrapper.jar" ]; then
  echo "Gradle Wrapper já existe."
  exit 0
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
( cd "$TMP" && gradle wrapper --gradle-version "$VERSION" --distribution-type bin )
mkdir -p "$ROOT/gradle/wrapper"
cp "$TMP/gradlew" "$TMP/gradlew.bat" "$ROOT/"
cp "$TMP/gradle/wrapper/gradle-wrapper.jar" "$TMP/gradle/wrapper/gradle-wrapper.properties" "$ROOT/gradle/wrapper/"
chmod +x "$ROOT/gradlew"
echo "Gradle Wrapper $VERSION criado."
