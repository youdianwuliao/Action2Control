#!/bin/sh
# Gradle wrapper script
set -e

APP_BASE_NAME=$(basename "$0")
APP_HOME=$( cd "${0%/*}" && pwd )

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

exec java -Xmx64m -Xms64m -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
