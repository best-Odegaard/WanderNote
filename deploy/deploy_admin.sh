#!/usr/bin/env bash
# ============================================================
# geek012 管理后台一键部署脚本（本地 → 服务器 <your-server-ip>）
#
# 部署内容：
#   1. 后端 jar（含新增 /admin/** 管理接口 + /feedback/submit）
#   2. 管理前端 admin-web/dist → /www/geek012/admin_h5（约 1.6MB 静态文件）
#   3. V1.8 建表 SQL（sys_admin/sys_role/user_feedback，幂等可重跑）
#   4. nginx 配置（listen 8082 托管 admin_h5 + 反代 /admin → 127.0.0.1:8081）
#
# 用法:
#   ./deploy_admin.sh           # 全量部署（后端 + 前端 + SQL + nginx）
#   ./deploy_admin.sh backend   # 只部署后端
#   ./deploy_admin.sh frontend  # 只部署管理前端
#   ./deploy_admin.sh sql       # 只执行 V1.8 SQL
#   ./deploy_admin.sh nginx     # 只更新 nginx 配置
# ============================================================
set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/your-key.pem}"
SSH_USER="${SSH_USER:-ubuntu}"
SSH_HOST="${SSH_HOST:-your-server-ip}"
SSH_OPTS="-i $SSH_KEY -o StrictHostKeyChecking=no -o ConnectTimeout=15"

# 服务器路径
REMOTE_DIR="/www/geek012"
REMOTE_ADMIN_H5="$REMOTE_DIR/admin_h5"
# 服务器为宝塔面板 nginx，站点配置目录：/www/server/panel/vhost/nginx/
REMOTE_NGINX_CONF="/www/server/panel/vhost/nginx/admin-8082.conf"
REMOTE_SQL_DIR="$REMOTE_DIR/sql_admin"
# 与服务器实际 MySQL 凭据一致（deploy.sh 同款）；--default-character-set=utf8mb4 防止中文乱码
MYSQL_CMD="mysql -uroot -p${MYSQL_PASSWORD:-your-mysql-password} --default-character-set=utf8mb4 geek012"

# 本地路径
LOCAL_JAR="$PROJECT_ROOT/backend/gkv-server/target/gkv-server-1.0-SNAPSHOT.jar"
LOCAL_ADMIN_DIST="$PROJECT_ROOT/admin-web/dist"
LOCAL_SQL="$PROJECT_ROOT/backend/sql/V1.8__create_admin_feedback.sql"
LOCAL_NGINX="$PROJECT_ROOT/admin-nginx.conf"

# ---------- 工具函数 ----------
log()  { printf "\033[1;32m[deploy-admin]\033[0m %s\n" "$*"; }
warn() { printf "\033[1;33m[deploy-admin]\033[0m %s\n" "$*"; }
err()  { printf "\033[1;31m[deploy-admin]\033[0m %s\n" "$*"; exit 1; }

ssh_run() {
  ssh $SSH_OPTS "$SSH_USER@$SSH_HOST" "$@"
}

# ---------- 检查 SSH 可达 ----------
log "检查服务器连通性 ($SSH_HOST)..."
ssh_run "hostname" > /dev/null 2>&1 || err "无法连接服务器，请检查密钥/网络"

# ---------- 解析参数 ----------
MODE="${1:-all}"
case "$MODE" in
  all|backend|frontend|sql|nginx) ;;
  *) err "未知参数: $MODE（可用: all|backend|frontend|sql|nginx）" ;;
esac

# ---------- 1. 部署后端 ----------
if [ "$MODE" = "backend" ] || [ "$MODE" = "all" ]; then
  log "===== 1. 构建并部署后端 ====="
  ( cd "$PROJECT_ROOT/backend" && mvn clean package -DskipTests -q ) || err "后端构建失败"
  [ -f "$LOCAL_JAR" ] || err "jar 未生成: $LOCAL_JAR"

  log "上传 jar 到服务器..."
  scp $SSH_OPTS "$LOCAL_JAR" "$SSH_USER@$SSH_HOST:$REMOTE_DIR/gkv-server.jar.new" > /dev/null 2>&1
  ssh_run "cd $REMOTE_DIR && md5sum gkv-server.jar.new" | awk '{print "  远程 MD5: "$1}'
  LOCAL_MD5=$(md5sum "$LOCAL_JAR" | awk '{print $1}')
  REMOTE_MD5=$(ssh_run "cd $REMOTE_DIR && md5sum gkv-server.jar.new" | awk '{print $1}')
  [ "$LOCAL_MD5" = "$REMOTE_MD5" ] || err "jar MD5 不一致，上传失败"

  log "替换 jar 并重启后端（监听 8081，nginx 反代）..."
  ssh_run "cd $REMOTE_DIR && sudo mv gkv-server.jar.new gkv-server.jar" || true
  ssh_run "sudo pkill -f 'gkv-server[.]jar' 2>/dev/null; sleep 2" || true
  ssh_run "cd $REMOTE_DIR && sudo bash -c 'nohup java -jar gkv-server.jar --server.port=8081 > /www/geek012/backend.log 2>&1 &' && echo OK" || true
  sleep 15
  HTTP_CODE=$(ssh_run "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8081/travel/chat" 2>/dev/null || echo 000)
  [ "$HTTP_CODE" = "200" ] && log "后端重启成功 (/travel/chat → $HTTP_CODE)" || warn "后端可能仍在启动 (HTTP $HTTP_CODE)"
fi

# ---------- 2. 部署管理前端 ----------
if [ "$MODE" = "frontend" ] || [ "$MODE" = "all" ]; then
  log "===== 2. 构建并部署管理前端 ====="
  ( cd "$PROJECT_ROOT/admin-web" && npm run build ) > /dev/null 2>&1 || err "管理前端构建失败"
  [ -d "$LOCAL_ADMIN_DIST" ] || err "dist 不存在: $LOCAL_ADMIN_DIST"

  log "上传 admin-web 到服务器..."
  ssh_run "sudo mkdir -p $REMOTE_DIR/admin_h5_new && sudo chown ubuntu:ubuntu $REMOTE_DIR/admin_h5_new"
  scp $SSH_OPTS -r "$LOCAL_ADMIN_DIST/." "$SSH_USER@$SSH_HOST:$REMOTE_DIR/admin_h5_new/" > /dev/null 2>&1
  FILE_COUNT=$(ssh_run "find $REMOTE_DIR/admin_h5_new -type f | wc -l")
  log "已上传 $FILE_COUNT 个文件"

  log "替换正式目录（旧版备份为 admin_h5.bak）..."
  ssh_run "cd $REMOTE_DIR && sudo rm -rf admin_h5.bak && { [ -d admin_h5 ] && sudo mv admin_h5 admin_h5.bak; } ; sudo mv admin_h5_new admin_h5 && sudo chown -R www-data:www-data admin_h5 && echo OK"
fi

# ---------- 3. 执行 SQL ----------
if [ "$MODE" = "sql" ] || [ "$MODE" = "all" ]; then
  log "===== 3. 执行 V1.8 建表 SQL（幂等）====="
  ssh_run "sudo mkdir -p $REMOTE_SQL_DIR && sudo chown ubuntu:ubuntu $REMOTE_SQL_DIR"
  scp $SSH_OPTS "$LOCAL_SQL" "$SSH_USER@$SSH_HOST:$REMOTE_SQL_DIR/" > /dev/null 2>&1
  ssh_run "$MYSQL_CMD < $REMOTE_SQL_DIR/V1.8__create_admin_feedback.sql" \
    && log "SQL 执行成功" \
    || warn "SQL 执行失败，请人工检查（服务器 MySQL 凭据/库名）: ssh 后执行 $MYSQL_CMD < $REMOTE_SQL_DIR/V1.8__create_admin_feedback.sql"
fi

# ---------- 4. 部署 nginx 配置 ----------
if [ "$MODE" = "nginx" ] || [ "$MODE" = "all" ]; then
  log "===== 4. 部署 nginx 配置（监听 8082）====="
  scp $SSH_OPTS "$LOCAL_NGINX" "$SSH_USER@$SSH_HOST:/tmp/admin-8082.conf" > /dev/null 2>&1
  ssh_run "sudo cp /tmp/admin-8082.conf $REMOTE_NGINX_CONF" || warn "拷贝 nginx 配置失败，请手动复制 admin-nginx.conf 到 $REMOTE_NGINX_CONF"
  ssh_run "sudo nginx -t" > /dev/null 2>&1 || { warn "nginx -t 校验失败，请人工检查配置"; exit 1; }
  ssh_run "sudo nginx -s reload" > /dev/null 2>&1 && log "nginx 已重载"
fi

# ---------- 验证 ----------
if [ "$MODE" = "all" ]; then
  log "===== 验证 ====="
  sleep 2
  ssh_run "curl -s -o /dev/null -w '管理端页面(8082): %{http_code}\n' http://127.0.0.1:8082/" 2>/dev/null || true
  ssh_run "curl -s -X POST http://127.0.0.1:8082/admin/login -H 'Content-Type: application/json' -d '{\"username\":\"admin\",\"password\":\"admin123\"}' | head -c 150" 2>/dev/null || true
  echo
fi

log "部署完成。浏览器访问: http://$SSH_HOST:8082/  （初始账号 admin / admin123，首次登录后请尽快修改密码）"
