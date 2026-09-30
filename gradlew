#!/bin/sh
# Gradle wrapper launcher
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ -n "$JAVA_HOME" ]; then JAVACMD="$JAVA_HOME/bin/java"; else JAVACMD=java; fi
command -v "$JAVACMD" >/dev/null 2>&1 || { echo "ERREUR : Java introuvable. Installez Java 17 ou définissez JAVA_HOME." >&2; exit 1; }
exec "$JAVACMD" $JAVA_OPTS $GRADLE_OPTS -Xmx64m -Xms64m "-Dorg.gradle.appname=gradlew" \
  -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
