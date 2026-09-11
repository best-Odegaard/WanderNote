#!/usr/bin/env bash
# ============================================================
# geek012 一键部署脚本（本地 → 服务器 <your-server-ip>）
#
# 用法:
#   ./deploy.sh            # 自动检测后端/前端/SQL 改动，只部署有改动的部分
#   ./deploy.sh backend    # 强制部署后端（重新构建 jar + 重启）
#   ./deploy.sh frontend   # 强制部署前端（构建 H5 + 上传）
#   ./deploy.sh sql        # 执行 SQL 脚本补表
#   ./deploy.sh all        # 全量部署（后端 + 前端 + SQL）
#
# 注意: agent 服务不在本脚本范围（服务器上已有有效 API Key，本地 Key 无效）
# ============================================================
set -e

# ---------- 常量配置 ----------
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/your-key.pem}"
SSH_USER="${SSH_USER:-ubuntu}"
SSH_HOST="${SSH_HOST:-your-server-ip}"
SSH_OPTS="-i $SSH_KEY -o StrictHostKeyChecking=no -o ConnectTimeout=15"

# 服务器路径
REMOTE_BACKEND_DIR="/www/geek012"
REMOTE_BACKEND_JAR="$REMOTE_BACKEND_DIR/gkv-server.jar"
REMOTE_FRONTEND_DIR="$REMOTE_BACKEND_DIR/frontend_h5"
REMOTE_SQL_DIR="$REMOTE_BACKEND_DIR/sql_deploy"
REMOTE_BACKEND_LOG="$REMOTE_BACKEND_DIR/backend.log"

# 本地路径
LOCAL_BACKEND="$PROJECT_ROOT/backend"
LOCAL_JAR="$LOCAL_BACKEND/gkv-server/target/gkv-server-1.0-SNAPSHOT.jar"
LOCAL_H5="$PROJECT_ROOT/frontend/dist/build/h5"
LOCAL_SQL_DIR="$LOCAL_BACKEND/sql"

# 部署标记文件（记录上次部署时间，用于自动判断改动）
MARKER="$PROJECT_ROOT/.deploy_marker"

# 前端构建时强制指向远程 API（.env 里是 localhost 开发地址，部署必须覆盖）
REMOTE_API_URL="${REMOTE_API_URL:-http://$SSH_HOST:8080}"

# ---------- 工具函数 ----------
log()  { printf "\033[1;32m[deploy]\033[0m %s\n" "$*"; }
warn() { printf "\033[1;33m[deploy]\033[0m %s\n" "$*"; }
err()  { printf "\033[1;31m[deploy]\033[0m %s\n" "$*"; exit 1; }

ssh_run() {
  ssh $SSH_OPTS "$SSH_USER@$SSH_HOST" "$@"
}

# 自上次部署后是否有改动的文件（$1=目录, $2=marker）
has_changes_since() {
  local dir="$1" marker="$2"
  if [ ! -f "$marker" ]; then
    return 0  # 没有标记文件，视为有改动
  fi
  if find "$dir" -type f \( -name '*.java' -o -name '*.xml' -o -name '*.yml' -o -name '*.yaml' -o -name '*.sql' -o -name '*.vue' -o -name '*.ts' -o -name '*.scss' -o -name '*.json' -o -name '*.env*' -o -name '*.js' \) -newer "$marker" 2>/dev/null | grep -q .; then
    return 0
  fi
  return 1
}

# ---------- 检查 SSH 可达 ----------
log "检查服务器连通性 ($SSH_HOST)..."
ssh_run "hostname" > /dev/null 2>&1 || err "无法连接服务器，请检查密钥/网络"

# ---------- 解析参数 ----------
MODE="${1:-auto}"
case "$MODE" in
  auto|backend|frontend|sql|all) ;;
  *) err "未知参数: $MODE（可用: auto|backend|frontend|sql|all）" ;;
esac

# ---------- 自动判断改动 ----------
if [ "$MODE" = "auto" ]; then
  DO_BACKEND=false; DO_FRONTEND=false; DO_SQL=false
  [ -f "$MARKER" ] && log "上次部署: $(date -r "$MARKER" '+%F %T')" || warn "首次部署，将执行全量部署"

  if has_changes_since "$LOCAL_BACKEND/gkv-server/src" "$MARKER" || has_changes_since "$LOCAL_BACKEND/pom.xml" "$MARKER" || has_changes_since "$LOCAL_BACKEND/gkv-server/pom.xml" "$MARKER"; then
    DO_BACKEND=true; log "检测到后端代码改动"
  fi
  if has_changes_since "$PROJECT_ROOT/frontend/src" "$MARKER" || has_changes_since "$PROJECT_ROOT/frontend/package.json" "$MARKER"; then
    DO_FRONTEND=true; log "检测到前端代码改动"
  fi
  if has_changes_since "$LOCAL_SQL_DIR" "$MARKER"; then
    DO_SQL=true; log "检测到 SQL 脚本改动"
  fi

  if [ "$DO_BACKEND" = false ] && [ "$DO_FRONTEND" = false ] && [ "$DO_SQL" = false ]; then
    log "自上次部署后无代码改动，无需部署。"
    exit 0
  fi
else
  DO_BACKEND=false; DO_FRONTEND=false; DO_SQL=false
  [ "$MODE" = "backend" -o "$MODE" = "all" ] && DO_BACKEND=true
  [ "$MODE" = "frontend" -o "$MODE" = "all" ] && DO_FRONTEND=true
  [ "$MODE" = "sql" -o "$MODE" = "all" ] && DO_SQL=true
fi

# ---------- 1. 部署后端 ----------
if [ "$DO_BACKEND" = true ]; then
  log "===== 部署后端 ====="

  # 1.1 停止本地后端（避免占用 jar 导致 clean 失败）
  LOCAL_PID=$(netstat -ano 2>/dev/null | grep -E 'LISTENING' | grep ':8080 ' | awk '{print $NF}' | head -1)
  if [ -n "$LOCAL_PID" ]; then
    log "停止本地后端进程 PID=$LOCAL_PID（释放 jar 占用）"
    taskkill //F //PID "$LOCAL_PID" > /dev/null 2>&1 || warn "停止本地后端失败（忽略）"
    sleep 2
  fi

  # 1.2 构建 jar
  log "Maven 构建中（跳过测试）..."
  ( cd "$LOCAL_BACKEND" && mvn clean package -DskipTests -q ) || err "后端构建失败"
  [ -f "$LOCAL_JAR" ] || err "jar 未生成: $LOCAL_JAR"

  # 1.3 上传（先传临时名，校验后替换，避免传一半覆盖）
  log "上传 jar 到服务器..."
  scp $SSH_OPTS "$LOCAL_JAR" "$SSH_USER@$SSH_HOST:$REMOTE_BACKEND_DIR/gkv-server.jar.new" > /dev/null 2>&1
  ssh_run "cd $REMOTE_BACKEND_DIR && md5sum gkv-server.jar.new" | awk '{print "  远程 MD5: "$1}'
  LOCAL_MD5=$(md5sum "$LOCAL_JAR" | awk '{print $1}')
  REMOTE_MD5=$(ssh_run "cd $REMOTE_BACKEND_DIR && md5sum gkv-server.jar.new" | awk '{print $1}')
  [ "$LOCAL_MD5" = "$REMOTE_MD5" ] || err "jar MD5 不一致，上传失败"

  # 1.4 替换 + 重启
  log "替换 jar 并重启后端..."
  # 重要：pkill 与启动拆成两条 ssh，且 pkill 模式用 [.] 正则技巧——
  # 否则 pkill -f 会匹配到本命令行的 "java -jar gkv-server.jar" 字面量，误杀自身导致启动失败
  # 服务器上 nginx 占用 8080 并反代到 8081，故 jar 必须监听 8081
  # 日志路径写死绝对路径：远程单引号内 $REMOTE_BACKEND_LOG 不会被展开
  ssh_run "cd $REMOTE_BACKEND_DIR && sudo mv gkv-server.jar.new gkv-server.jar" || true
  ssh_run "sudo pkill -f 'gkv-server[.]jar' 2>/dev/null; sleep 2" || true
  ssh_run "cd $REMOTE_BACKEND_DIR && sudo bash -c 'nohup java -jar gkv-server.jar --server.port=8081 > /www/geek012/backend.log 2>&1 &' && echo OK" || true
  sleep 15
  HTTP_CODE=$(ssh_run "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/travel/chat" 2>/dev/null || echo 000)
  [ "$HTTP_CODE" = "200" ] && log "后端重启成功 (/travel/chat → $HTTP_CODE)" || warn "后端可能仍在启动中 (HTTP $HTTP_CODE)，请稍后检查 $REMOTE_BACKEND_LOG"
fi

# ---------- 2. 部署前端 ----------
if [ "$DO_FRONTEND" = true ]; then
  log "===== 部署前端 H5 ====="

  # 2.1 构建（强制覆盖 API 地址为远程）
  log "构建 H5（API 指向 $REMOTE_API_URL）..."
  ( cd "$PROJECT_ROOT/frontend" && VITE_API_BASE_URL="$REMOTE_API_URL" npm run build:h5 ) > /dev/null 2>&1 || err "前端构建失败"
  [ -d "$LOCAL_H5" ] || err "H5 产物不存在: $LOCAL_H5"

  # 2.2 上传到临时目录
  log "上传 H5 到服务器..."
  ssh_run "sudo mkdir -p $REMOTE_BACKEND_DIR/frontend_h5_new && sudo chown ubuntu:ubuntu $REMOTE_BACKEND_DIR/frontend_h5_new"
  scp $SSH_OPTS -r "$LOCAL_H5/." "$SSH_USER@$SSH_HOST:$REMOTE_BACKEND_DIR/frontend_h5_new/" > /dev/null 2>&1
  FILE_COUNT=$(ssh_run "find $REMOTE_BACKEND_DIR/frontend_h5_new -type f | wc -l")
  log "已上传 $FILE_COUNT 个文件"

  # 2.3 替换正式目录（备份旧的）
  log "替换正式目录（旧版已备份）..."
  ssh_run "cd $REMOTE_BACKEND_DIR && sudo rm -rf frontend_h5.bak && sudo mv frontend_h5 frontend_h5.bak && sudo mv frontend_h5_new frontend_h5 && sudo chown -R www-data:www-data frontend_h5 && sudo nginx -s reload 2>/dev/null; echo OK"
  sleep 2
  HTTP_CODE=$(ssh_run "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1/index.html" 2>/dev/null || echo 000)
  [ "$HTTP_CODE" = "200" ] && log "前端部署成功 (H5 → $HTTP_CODE)" || warn "H5 验证异常 (HTTP $HTTP_CODE)"
fi
if [ "$DO_SQL" = true ]; then
  log "===== 部署 SQL ====="
  sql_files=$(ls "$LOCAL_SQL_DIR"/*.sql 2>/dev/null || true)
  if [ -z "$sql_files" ]; then
    warn "SQL 目录无脚本"
  else
    log "发现 SQL 脚本:"
    for f in "$LOCAL_SQL_DIR"/*.sql; do
      log "  - $(basename "$f")"
    done
    log "上传 SQL 到服务器..."
    ssh_run "sudo mkdir -p $REMOTE_SQL_DIR && sudo chown ubuntu:ubuntu $REMOTE_SQL_DIR"
    scp $SSH_OPTS "$LOCAL_SQL_DIR"/*.sql "$SSH_USER@$SSH_HOST:$REMOTE_SQL_DIR/" > /dev/null 2>&1
    log "已上传。如需执行请手动运行: ssh 后执行 mysql -uroot -p<your-mysql-password> geek012 < <脚本名>"
    log "（SQL 脚本可能存在 DROP/修改语句，脚本默认不自动执行，请人工确认）"
  fi
fi

# ---------- 更新部署标记 ----------
touch "$MARKER"
log "部署完成，已更新部署标记 $(date '+%F %T')"
log "服务器: http://<your-server-ip>/  (后端 :8080, agent :8002 未动)"
