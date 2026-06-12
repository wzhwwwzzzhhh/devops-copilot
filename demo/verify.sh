#!/bin/bash
# ============================================
# DevOps Copilot — 故障模拟场景验证脚本
# 用法: bash demo/verify.sh [scenario_number]
# 示例: bash demo/verify.sh 1    # 只验证场景 1
#       bash demo/verify.sh      # 验证所有场景
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCENARIOS_DIR="$SCRIPT_DIR/scenarios"
MYSQL_CMD=${MYSQL_CMD:-mysql}
MYSQL_HOST=${MYSQL_HOST:-127.0.0.1}
MYSQL_PORT=${MYSQL_PORT:-3306}
MYSQL_USER=${MYSQL_USER:-root}
MYSQL_PASS=${MYSQL_PASS:-}

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 数据库连接
MYSQL_ARGS="-h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER"
if [ -n "$MYSQL_PASS" ]; then
    MYSQL_ARGS="$MYSQL_ARGS -p$MYSQL_PASS"
fi

echo "============================================"
echo " DevOps Copilot — 故障模拟场景验证"
echo " 数据库: $MYSQL_HOST:$MYSQL_PORT"
echo "============================================"
echo ""

# 测试 MySQL 连接
if ! $MYSQL_CMD $MYSQL_ARGS -e "SELECT 1" > /dev/null 2>&1; then
    echo -e "${RED}❌ 无法连接到 MySQL，请设置 MYSQL_HOST/PORT/USER/PASS${NC}"
    echo "   示例: MYSQL_PASS=myrootpwd bash demo/verify.sh"
    exit 1
fi

echo -e "${GREEN}✅ MySQL 连接成功${NC}"
echo ""

TESTS_DIR="$SCRIPT_DIR/tmp_test"
mkdir -p "$TESTS_DIR"

run_scenario() {
    local sql_file="$1"
    local num="$2"
    local name="$3"
    local output_file="$TESTS_DIR/result_$(basename "$sql_file" .sql).log"

    echo -n "  [场景 $num] $name ... "

    # 执行 SQL 脚本（捕获错误但不中断）
    if $MYSQL_CMD $MYSQL_ARGS --force < "$sql_file" > "$output_file" 2>&1; then
        # 检查输出中是否有关键词
        if grep -qi "error\|deadlock\|timeout\|warning" "$output_file" 2>/dev/null; then
            echo -e "${YELLOW}⚠️  执行完成（有关键词触发）${NC}"
        else
            echo -e "${GREEN}✅ 执行完成${NC}"
        fi
    else
        echo -e "${RED}❌ 执行失败${NC}"
    fi
}

# 如果没有指定场景号，运行所有场景
SPECIFIC_SCENARIO="${1:-}"

echo "开始验证..."
echo ""

if [ -n "$SPECIFIC_SCENARIO" ]; then
    num=$(printf "%02d" $SPECIFIC_SCENARIO)
    for f in "$SCENARIOS_DIR"/$num-*.sql; do
        if [ -f "$f" ]; then
            name=$(basename "$f" .sql)
            run_scenario "$f" "$SPECIFIC_SCENARIO" "$name"
        fi
    done
else
    count=0
    for f in "$SCENARIOS_DIR"/*.sql; do
        count=$((count + 1))
        filename=$(basename "$f" .sql)
        num=$(echo "$filename" | cut -d- -f1)
        name=$(echo "$filename" | cut -d- -f2-)
        run_scenario "$f" "$num" "$name"
    done
    echo ""
    echo -e "${GREEN}✅ 已完成 $count 个场景验证${NC}"
fi

echo ""
echo "验证日志目录: $TESTS_DIR"

# 清理（可选）
# rm -rf "$TESTS_DIR"

echo "============================================"
