#!/usr/bin/env bash
# 管理网页登录密码设置工具（memorygames-pk / :8091）。
#
# 在云服务器上以 root 运行：
#   bash set-admin-password.sh             # 交互设置或修改固定密码
#   bash set-admin-password.sh --disable   # 移除密码配置，恢复仅 Token/内网访问
#
# 依赖同目录 hashtool/GenHash.java 与 hashtool/ext/（由 app.jar 解出，
# 部署时已放置；缺失时见 docs/admin-console.md）。
set -euo pipefail

DIR=/root/memorygames-pk
HASHTOOL="$DIR/hashtool"
DROPIN=/etc/systemd/system/memorygames-pk.service.d/admin-password.conf

if [ "${1:-}" = "--disable" ]; then
    rm -f "$DROPIN"
    systemctl daemon-reload
    systemctl restart memorygames-pk
    echo "已移除管理员密码配置并重启服务（网页登录恢复为未启用）。"
    exit 0
fi
if [ "$#" -ne 0 ]; then
    echo "用法: $0 [--disable]" >&2
    exit 2
fi

cd "$HASHTOOL" 2>/dev/null || { mkdir -p "$HASHTOOL"; cd "$HASHTOOL"; }
if [ ! -f GenHash.java ]; then
    echo "缺少 GenHash.java（部署时随包放置）。" >&2
    exit 1
fi
mkdir -p ext
unzip -o -q "$DIR/app.jar" \
    'BOOT-INF/classes/com/memorygames/pk/admin/AdminPasswordHash.class' \
    'BOOT-INF/lib/spring-security-crypto-*.jar' \
    'BOOT-INF/lib/spring-jcl-*.jar' \
    -d ext

read -rsp "新管理员密码（至少 12 字符，最多 72 字节）: " PW1; echo
read -rsp "再输入一次确认: " PW2; echo
if [ "$PW1" != "$PW2" ]; then
    echo "两次输入不一致。" >&2
    exit 1
fi
unset PW2

HASH=$(printf '%s' "$PW1" | java -cp "ext/BOOT-INF/classes:ext/BOOT-INF/lib/*" GenHash.java) || {
    echo "哈希计算失败。" >&2
    unset PW1
    exit 1
}
case "$HASH" in
    \$2*) ;;
    *) echo "哈希输出异常，未写入配置。" >&2; unset PW1; exit 1 ;;
esac

umask 077
cat > "$DROPIN" <<EOF
# Browser admin password (bcrypt hash). Managed by set-admin-password.sh.
[Service]
Environment=PK_ADMIN_PASSWORD_HASH=$HASH
EOF
unset HASH
systemctl daemon-reload
systemctl restart memorygames-pk
echo "服务已重启，等待健康检查 ..."

UP=no
for _ in $(seq 1 40); do
    if curl -fsS -m 2 http://127.0.0.1:8091/actuator/health >/dev/null 2>&1; then
        UP=yes
        break
    fi
    sleep 1
done
if [ "$UP" != yes ]; then
    echo "警告：重启后服务未健康，请检查 journalctl -u memorygames-pk -n 50" >&2
    unset PW1
    exit 1
fi

ESCAPED=$(printf '%s' "$PW1" | sed -e 's/\\/\\\\/g; s/"/\\"/g')
CODE=$(curl -s -o /dev/null -w '%{http_code}' -m 5 \
    -H 'Content-Type: application/json' \
    -d "{\"password\":\"$ESCAPED\"}" \
    http://127.0.0.1:8091/api/admin/auth/login)
unset PW1 ESCAPED

if [ "$CODE" = "200" ]; then
    echo "完成：密码已保存，登录自测通过（HTTP 200）。"
    echo "在本机开 SSH 隧道后访问管理控制台："
    echo "  ssh -N -L 18091:127.0.0.1:8091 root@124.220.35.87"
    echo "  浏览器打开 http://127.0.0.1:18091/api/admin/console"
    echo "以后修改密码：重新运行本脚本；停用网页登录：bash $0 --disable"
else
    echo "警告：配置已写入，但登录自测返回 HTTP $CODE。" >&2
    echo "排查：journalctl -u memorygames-pk -n 50" >&2
    echo "回滚：bash $0 --disable" >&2
    exit 1
fi
