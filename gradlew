#!/bin/sh
echo "Downloading gradle..."
curl -s https://services.gradle.org/distributions/gradle-8.4-bin.zip -o /tmp/gradle.zip 2>/dev/null
# pakai gradle action di github aja
./gradlew.real $@
