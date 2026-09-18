#!/usr/bin/env bash
# geek012-flash 本地构建辅助脚本
#
# 为什么不用 PATH 上的 mvn：
#   系统里三套 Maven 的 bin/mvn 都是 sh 脚本，在这个 Git Bash 环境下 cygpath 转换没生效，
#   于是把 /d/... 这种 MSYS 路径直接喂给了 Windows 版 java.exe，
#   结果是「找不到或无法加载主类 org.codehaus.plexus.classworlds.launcher.Launcher」。
#   这里改成直接调 java + Windows 风格路径，绕开转换问题。
#
# 另外：JDK 17 + maven-compiler-plugin 3.10.x + Lombok 1.18.20 的兼容性见调用方说明。
set -e

JAVA_EXE="${JAVA_EXE:-D:/java/bin/java}"
MAVEN_HOME_WIN="${MAVEN_HOME_WIN:-D:/apache-maven-3.9.12}"
PROJECT_DIR="${PROJECT_DIR:-D:/geek/AI文旅/geek012-flash/backend}"

exec "$JAVA_EXE" \
  -classpath "$MAVEN_HOME_WIN/boot/plexus-classworlds-2.9.0.jar" \
  -Dclassworlds.conf="$MAVEN_HOME_WIN/bin/m2.conf" \
  -Dmaven.home="$MAVEN_HOME_WIN" \
  -Dmaven.multiModuleProjectDirectory="$PROJECT_DIR" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
