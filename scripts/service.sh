#!/usr/bin/env bash
# ============================================================================
# lhjypt 后端常驻托管（P3-002）
#
#   ./scripts/service.sh install    安装为常驻服务（macOS launchd / Linux systemd）
#   ./scripts/service.sh uninstall  卸载常驻服务
#   ./scripts/service.sh status     查看常驻状态 + 健康检查
#   ./scripts/service.sh logs       跟踪后端日志
#
# 特性：崩溃自动拉起（KeepAlive/Restart=always）、开机/登录自启、日志落 logs/
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LABEL="com.lhjypt.backend"
JAR="$ROOT/core/target/core-0.1.0-SNAPSHOT.jar"

resolve_java() {
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    echo "$JAVA_HOME/bin/java"; return
  fi
  for c in /opt/homebrew/opt/openjdk@17/bin/java /usr/local/opt/openjdk@17/bin/java; do
    [ -x "$c" ] && { echo "$c"; return; }
  done
  if /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
    echo "$(/usr/libexec/java_home -v 17)/bin/java"; return
  fi
  command -v java
}

JAVA_BIN="$(resolve_java)"
[ -x "$JAVA_BIN" ] || { echo "未找到 Java 17：请设置 JAVA_HOME 或安装 openjdk@17"; exit 1; }
mkdir -p "$ROOT/logs"

OS="$(uname -s)"

install_macos() {
  local plist="$HOME/Library/LaunchAgents/$LABEL.plist"
  mkdir -p "$HOME/Library/LaunchAgents"
  # 先停掉 dev.sh 方式启动的实例，避免端口冲突
  pkill -f 'core-0.1.0-SNAPSHOT.jar' 2>/dev/null || true
  rm -f "$ROOT/.runtime/backend.pid"
  cat > "$plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>$LABEL</string>
  <key>ProgramArguments</key>
  <array>
    <string>$JAVA_BIN</string>
    <string>-jar</string>
    <string>$JAR</string>
  </array>
  <key>WorkingDirectory</key><string>$ROOT</string>
  <key>EnvironmentVariables</key>
  <dict>
    <key>DOCKER_HOST</key><string>unix://$HOME/.lima/tuna-docker/sock/docker.sock</string>
    <key>DOCKER_API_VERSION</key><string>1.44</string>
    <key>LHJYPT_TRADE_MODE</key><string>MOCK</string>
  </dict>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
  <key>StandardOutPath</key><string>$ROOT/logs/backend-launchd.log</string>
  <key>StandardErrorPath</key><string>$ROOT/logs/backend-launchd.log</string>
</dict>
</plist>
PLIST
  launchctl bootout "gui/$(id -u)/$LABEL" 2>/dev/null || true
  launchctl bootstrap "gui/$(id -u)" "$plist"
  echo "[service] 已安装并启动（launchd）: $plist"
  sleep 6
  status_macos
}

uninstall_macos() {
  launchctl bootout "gui/$(id -u)/$LABEL" 2>/dev/null || true
  rm -f "$HOME/Library/LaunchAgents/$LABEL.plist"
  echo "[service] 已卸载（launchd）"
}

status_macos() {
  echo "[service] launchctl 状态:"
  launchctl print "gui/$(id -u)/$LABEL" 2>/dev/null | grep -E "state = |pid = |last exit code" | sed 's/^/  /' || echo "  未安装"
  echo -n "[service] 健康检查: "
  curl -s -m 4 http://localhost:8081/actuator/health | head -c 40 || echo "未响应"
  echo
}

install_linux() {
  local unit="/etc/systemd/system/lhjypt-backend.service"
  cat > /tmp/lhjypt-backend.service <<UNIT
[Unit]
Description=lhjypt core backend
After=network.target

[Service]
Type=simple
WorkingDirectory=$ROOT
Environment=LHJYPT_TRADE_MODE=MOCK
ExecStart=$JAVA_BIN -jar $JAR
Restart=always
RestartSec=5
StandardOutput=append:$ROOT/logs/backend-systemd.log
StandardError=append:$ROOT/logs/backend-systemd.log

[Install]
WantedBy=multi-user.target
UNIT
  echo "[service] 生成 unit: /tmp/lhjypt-backend.service"
  echo "[service] 请执行（需 sudo）:"
  echo "  sudo cp /tmp/lhjypt-backend.service $unit"
  echo "  sudo systemctl daemon-reload && sudo systemctl enable --now lhjypt-backend"
}

case "${1:-status}" in
  install)
    if [ "$OS" = "Darwin" ]; then install_macos; else install_linux; fi;;
  uninstall)
    if [ "$OS" = "Darwin" ]; then uninstall_macos; else
      echo "[service] 请执行（需 sudo）: sudo systemctl disable --now lhjypt-backend && sudo rm /etc/systemd/system/lhjypt-backend.service"; fi;;
  status)
    if [ "$OS" = "Darwin" ]; then status_macos; else
      systemctl status lhjypt-backend --no-pager 2>/dev/null | head -8 || echo "未安装"; fi;;
  logs)
    tail -f "$ROOT/logs/backend-launchd.log" 2>/dev/null || tail -f "$ROOT/logs/backend.log";;
  *) echo "用法: $0 {install|uninstall|status|logs}"; exit 1;;
esac