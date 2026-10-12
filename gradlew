#!/usr/bin/env sh
set -e
JAR="gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$JAR" ]; then
  mkdir -p gradle/wrapper
  curl -sL https://github.com/gradle/gradle/raw/master/gradle/wrapper/gradle-wrapper.jar -o $JAR
  curl -sL https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.properties -o gradle/wrapper/gradle-wrapper.properties
fi
exec java -jar $JAR "$@"
