#!/bin/sh
DIR=$(cd "$(dirname "$0")" && pwd)
JAVA_EXE=${JAVA_HOME:+$JAVA_HOME/bin/java}
JAVA_EXE=${JAVA_EXE:-java}
exec "$JAVA_EXE" -Dfile.encoding=UTF-8 -classpath "$DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
