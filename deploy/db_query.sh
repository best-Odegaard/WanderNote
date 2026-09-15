#!/usr/bin/env bash
# 用后端配置里的数据库账号执行一条 SQL（排查用）。密码只从配置文件读取，不会打印出来。
#
# 用法：
#   bash db_query.sh <application-dev.yml 路径> "<SQL>"
#
# 例子：
#   bash db_query.sh app.yml "SELECT COUNT(*) FROM banner"
set -e

CFG="${1:?用法: db_query.sh <配置文件> <SQL>}"
SQL="${2:?缺少 SQL}"

# 从 YAML 里取值：去掉 CR（配置是 CRLF 行尾）、键名、尾部空白和一层引号
pick() {
  grep -m1 "^[[:space:]]*$1:" "$CFG" \
    | sed -E "s/\r$//; s/^[^:]*:[[:space:]]*//; s/[[:space:]]*$//; s/^'(.*)'$/\1/; s/^\"(.*)\"$/\1/"
}

HOST=$(pick host)
PORT=$(pick port)
DB=$(pick database)
U=$(pick username)
P=$(pick password)

export MYSQL_PWD="$P"
exec mysql -u"$U" -h"${HOST:-127.0.0.1}" -P"${PORT:-3306}" "${DB:-geek012}" -N -e "$SQL"
