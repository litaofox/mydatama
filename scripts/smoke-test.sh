#!/usr/bin/env bash
# ============================================================
# 企业数据中台 MVP 冒烟测试 (T01~T10)
# 用法:
#   bash scripts/smoke-test.sh                          # 默认 BASE=http://localhost:8090
#   BASE=http://192.168.1.10:8090 bash scripts/smoke-test.sh
# Windows: 请使用 Git Bash 执行; 依赖 curl 与 python3/python(仅标准库)。
# 说明: 每步打印 [PASS]/[FAIL] 并累计; 单步失败不中断继续执行;
#       最后汇总, 存在失败时退出码为 1, 全部通过退出码为 0。
# ============================================================
set -u

BASE="${BASE:-http://localhost:8090}"
PY="$(command -v python3 || command -v python || true)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SAMPLE_GPS="$ROOT/samples/vehicle_gps_20260901.csv"
AUTH=""          # admin token
PASS=0
FAIL=0

say()  { printf '%s\n' "$*"; }
pass() { PASS=$((PASS+1)); printf '[PASS] %s %s %s\n' "$1" "$2" "${3:-}"; }
fail() { FAIL=$((FAIL+1)); printf '[FAIL] %s %s %s\n' "$1" "$2" "${3:-}"; }
trunc() { printf '%s' "$1" | head -c 160; }

if [ -z "$PY" ]; then
  say "[FAIL] ENV 未找到 python3/python, 请先安装"
  exit 1
fi

# ---------- JSON 工具(递归查找键, 兼容任意嵌套; 驼峰/蛇形键名均兼容) ----------
PY_FIND='
import sys, json
key = sys.argv[1]
def find(d):
    if isinstance(d, dict):
        if key in d: return d[key]
        for v in d.values():
            r = find(v)
            if r is not None: return r
    elif isinstance(d, list):
        for v in d:
            r = find(v)
            if r is not None: return r
    return None
try:
    d = json.load(sys.stdin)
except Exception:
    sys.exit(2)
r = find(d)
if r is None: sys.exit(1)
print(r)
'
jfind() { printf '%s' "$1" | "$PY" -c "$PY_FIND" "$2" 2>/dev/null; }

# 输出 "PASS/FAIL|详情": metrics(五阶段)存在且>=5项且不含 fail/error
PY_METRICS='
import sys, json
try:
    d = json.load(sys.stdin)
except Exception:
    print("FAIL|no-json"); raise SystemExit
def collect(d, key, out):
    if isinstance(d, dict):
        for k, v in d.items():
            if k == key: out.append(v)
            collect(v, key, out)
    elif isinstance(d, list):
        for v in d: collect(v, key, out)
out = []
collect(d, "metrics", out)
m = out[0] if out else None
if (isinstance(m, (dict, list))) and len(m) >= 5:
    s = json.dumps(m, ensure_ascii=False).lower()
    ok = ("fail" not in s) and ("error" not in s)
    print(("PASS|" if ok else "FAIL|") + s[:180])
else:
    print("FAIL|metrics missing/short: " + json.dumps(m, ensure_ascii=False)[:120])
'

# 输出 "PASS 数值/FAIL": overallScore 或 overall_score 任一存在
PY_SCORE='
import sys, json
d = json.load(sys.stdin)
def collect(d, key, out):
    if isinstance(d, dict):
        for k, v in d.items():
            if k == key: out.append(v)
            collect(v, key, out)
    elif isinstance(d, list):
        for v in d: collect(v, key, out)
for key in ("overallScore", "overall_score"):
    out = []
    collect(d, key, out)
    if out and out[0] is not None:
        print("PASS " + str(out[0])); raise SystemExit
print("FAIL")
'

# 候选键计数: 依次找 keys, 列表取长度, 数值直接取值, 找不到输出 -1
PY_NCOUNT='
import sys, json
keys = sys.argv[1:]
d = json.load(sys.stdin)
def collect(d, key, out):
    if isinstance(d, dict):
        for k, v in d.items():
            if k == key: out.append(v)
            collect(v, key, out)
    elif isinstance(d, list):
        for v in d: collect(v, key, out)
def first_int():
    for key in keys:
        out = []
        collect(d, key, out)
        for v in out:
            if isinstance(v, list):
                return len(v)
            if isinstance(v, (int, float)):
                return int(v)
    return -1
print(first_int())
'

# ---------- HTTP 封装: req METHOD PATH [JSON_BODY] [TOKEN] → BODY / HTTP_CODE ----------
BODY=""
HTTP_CODE=""
req() {
  local method="$1" path="$2" body="${3:-}" token="${4:-}" resp
  local args=(-s -X "$method" "$BASE$path" -w $'\n%{http_code}')
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  if [ -n "$body" ]; then
    args+=(-H "Content-Type: application/json" -d "$body")
  fi
  resp="$(curl "${args[@]}" 2>/dev/null)"
  HTTP_CODE="${resp##*$'\n'}"
  BODY="$(printf '%s' "$resp" | sed '$d')"
}

# 轮询处理作业: poll_job FILEID → 输出最终状态(至多 30 次 × 3s = 90s), 响应存 LAST_JOB
LAST_JOB=""
poll_job() {
  local fid="$1" st="" i
  for i in $(seq 1 30); do
    LAST_JOB="$(curl -s -H "Authorization: Bearer $AUTH" "$BASE/api/processing/files/$fid/job" 2>/dev/null)"
    st="$(jfind "$LAST_JOB" status)"
    case "$st" in
      SUCCEEDED|FAILED) printf '%s' "$st"; return 0 ;;
    esac
    [ "$i" -lt 30 ] && sleep 3
  done
  printf '%s' "${st:-UNKNOWN}"
}

say "==== 企业数据中台 MVP 冒烟测试 | BASE=$BASE ===="

# ---------- T01 登录 ----------
req POST /api/auth/login '{"username":"admin","password":"Admin@123"}' ""
CODE="$(jfind "$BODY" code)"
TOKEN="$(jfind "$BODY" accessToken)"
[ -z "$TOKEN" ] && TOKEN="$(jfind "$BODY" token)"
if [ "$CODE" = "0" ] && [ -n "$TOKEN" ]; then
  AUTH="$TOKEN"
  pass T01 "admin 登录" "code=$CODE token=${TOKEN:0:12}..."
else
  fail T01 "admin 登录" "code=$CODE http=$HTTP_CODE body=$(trunc "$BODY")"
fi

# ---------- T02 上传 GPS 文件 + 轮询至 SUCCEEDED + metrics 五阶段 ----------
if [ ! -f "$SAMPLE_GPS" ]; then
  fail T02 "上传 vehicle_gps_20260901.csv" "样例文件不存在: $SAMPLE_GPS (先运行 python samples/generator/generate_all.py)"
else
  UP="$(curl -s -X POST "$BASE/api/processing/files" -H "Authorization: Bearer $AUTH" \
    -F "file=@$SAMPLE_GPS" -F "bizDomain=transport" -F "secretLevel=2" 2>/dev/null)"
  FILEID="$(jfind "$UP" fileId)"
  [ -z "$FILEID" ] && FILEID="$(jfind "$UP" id)"
  if [ -z "$FILEID" ]; then
    fail T02 "上传 vehicle_gps_20260901.csv" "未取得 fileId: $(trunc "$UP")"
  else
    say "     T02 fileId=$FILEID 开始轮询作业(至多90s)..."
    FINAL="$(poll_job "$FILEID")"
    METRICS_OUT="$(printf '%s' "$LAST_JOB" | "$PY" -c "$PY_METRICS" 2>/dev/null)"
    METRICS_OK="${METRICS_OUT%%|*}"
    if [ "$FINAL" = "SUCCEEDED" ] && [ "$METRICS_OK" = "PASS" ]; then
      pass T02 "上传+五阶段处理" "status=$FINAL metrics=${METRICS_OUT#*|}"
    else
      fail T02 "上传+五阶段处理" "status=$FINAL metrics=$METRICS_OUT"
    fi
  fi
fi

# ---------- T03 文件详情 ----------
if [ -n "${FILEID:-}" ]; then
  req GET "/api/processing/files/$FILEID" "" "$AUTH"
  ST="$(jfind "$BODY" status)"
  SCOLS="$(jfind "$BODY" sensitive_cols)"
  [ -z "$SCOLS" ] && SCOLS="$(jfind "$BODY" sensitiveCols)"
  QC="$(jfind "$BODY" quarantineCount)"
  [ -z "$QC" ] && QC="$(jfind "$BODY" quarantine_count)"
  if [ "$ST" = "READY" ] && [ "${SCOLS:-0}" -ge 2 ] 2>/dev/null && [ "${QC:-0}" -ge 50 ] 2>/dev/null; then
    pass T03 "文件详情" "status=$ST sensitive_cols=$SCOLS quarantineCount=$QC"
  else
    fail T03 "文件详情" "status=$ST sensitive_cols=${SCOLS:-N/A} quarantineCount=${QC:-N/A} body=$(trunc "$BODY")"
  fi

  # ---------- T04 修复 ----------
  req POST "/api/processing/files/$FILEID/repair" '{}' "$AUTH"
  RJOB="$(jfind "$BODY" jobId)"
  [ -z "$RJOB" ] && RJOB="$FILEID"
  RFINAL="$(poll_job "$RJOB")"
  if [ "$RFINAL" = "SUCCEEDED" ]; then
    pass T04 "脱敏修复" "repair job status=$RFINAL"
  else
    fail T04 "脱敏修复" "repair job status=$RFINAL http=$HTTP_CODE body=$(trunc "$BODY")"
  fi
else
  fail T03 "文件详情" "跳过(T02 未取得 fileId)"
  fail T04 "脱敏修复" "跳过(T02 未取得 fileId)"
fi

# ---------- T05 资产检索 ----------
req GET "/api/governance/assets?keyword=vehicle_gps" "" "$AUTH"
ACOUNT="$(printf '%s' "$BODY" | "$PY" -c "$PY_NCOUNT" list items records assets data total 2>/dev/null)"
if [ "${ACOUNT:--1}" -ge 1 ] 2>/dev/null; then
  pass T05 "资产检索" "命中 $ACOUNT 条"
else
  fail T05 "资产检索" "命中 ${ACOUNT:-N/A} 条 body=$(trunc "$BODY")"
fi

# ---------- T06 数据集 + 版本 + 质量分 ----------
req POST /api/dataset/datasets '{"name":"smoke_vehicle_gps","bizDomain":"transport","filterCond":{"modality":["STRUCTURED"]}}' "$AUTH"
DSID="$(jfind "$BODY" datasetId)"
[ -z "$DSID" ] && DSID="$(jfind "$BODY" id)"
if [ -z "$DSID" ]; then
  fail T06 "数据集+版本" "创建数据集失败: $(trunc "$BODY")"
else
  req POST "/api/dataset/datasets/$DSID/versions" "{\"fileId\":\"$FILEID\",\"version\":\"v1\",\"remark\":\"smoke-test\"}" "$AUTH"
  VERID="$(jfind "$BODY" versionId)"
  [ -z "$VERID" ] && VERID="$(jfind "$BODY" id)"
  SCORE_OUT="$(printf '%s' "$BODY" | "$PY" -c "$PY_SCORE" 2>/dev/null)"
  if [ "${SCORE_OUT%% *}" != "PASS" ] && [ -n "$DSID" ]; then
    req GET "/api/dataset/datasets/$DSID/versions/$VERID" "" "$AUTH"
    SCORE_OUT="$(printf '%s' "$BODY" | "$PY" -c "$PY_SCORE" 2>/dev/null)"
  fi
  if [ "${SCORE_OUT%% *}" = "PASS" ]; then
    pass T06 "数据集+版本质量分" "datasetId=$DSID versionId=$VERID overallScore=${SCORE_OUT#PASS }"
  else
    fail T06 "数据集+版本质量分" "overallScore/overall_score 未找到: $(trunc "$BODY")"
  fi
fi

# ---------- T07 数据产品: 模板→创建→配置→生成→合规 ----------
req GET /api/product/templates "" "$AUTH"
TCOUNT="$(printf '%s' "$BODY" | "$PY" -c "$PY_NCOUNT" templates data 2>/dev/null)"
TPLBODY="$BODY"
TPLID="$(jfind "$TPLBODY" templateId)"
[ -z "$TPLID" ] && TPLID="$(jfind "$TPLBODY" id)"
if [ "$TCOUNT" = "3" ] && [ -n "$TPLID" ]; then
  pass T07a "产品模板" "templates=$TCOUNT templateId=$TPLID"
else
  fail T07a "产品模板" "templates=${TCOUNT:-N/A}(期望3) templateId=${TPLID:-N/A}"
fi
if [ -n "${TPLID:-}" ]; then
  req POST /api/product/products "{\"templateId\":\"$TPLID\",\"datasetId\":\"$DSID\",\"datasetVersionId\":\"$VERID\",\"name\":\"smoke-product\"}" "$AUTH"
  PRODID="$(jfind "$BODY" productId)"
  [ -z "$PRODID" ] && PRODID="$(jfind "$BODY" id)"
  if [ -z "$PRODID" ]; then
    fail T07b "产品配置/生成/合规" "创建产品失败: $(trunc "$BODY")"
  else
    req POST "/api/product/products/$PRODID/configure" '{"outputFormat":"CSV","schedule":"manual"}' "$AUTH"
    req POST "/api/product/products/$PRODID/generate" '{}' "$AUTH"
    req POST "/api/product/products/$PRODID/compliance/run" '{}' "$AUTH"
    PSTATUS="$(jfind "$BODY" status)"
    case "$PSTATUS" in
      BLOCKED|PASSED)
        pass T07b "产品配置/生成/合规" "productId=$PRODID status=$PSTATUS (BLOCKED/PASSED 均视为通过)" ;;
      *)
        fail T07b "产品配置/生成/合规" "productId=$PRODID status=${PSTATUS:-N/A} body=$(trunc "$BODY")" ;;
    esac
  fi
else
  fail T07b "产品配置/生成/合规" "跳过(未取得模板ID)"
fi

# ---------- T08 大屏 ----------
req GET /api/screen/overview "" "$AUTH"
S1="$(jfind "$BODY" code)"
req GET /api/screen/product "" "$AUTH"
S2="$(jfind "$BODY" code)"
if [ "$S1" = "0" ] && [ "$S2" = "0" ]; then
  pass T08 "大屏接口" "overview code=$S1 product code=$S2"
else
  fail T08 "大屏接口" "overview code=${S1:-N/A} product code=${S2:-N/A} http=$HTTP_CODE"
fi

# ---------- T09 审计日志 ----------
req GET "/api/iam/audit-logs" "" "$AUTH"
ATOTAL="$(printf '%s' "$BODY" | "$PY" -c "$PY_NCOUNT" total data 2>/dev/null)"
if [ "${ATOTAL:--1}" -gt 0 ] 2>/dev/null; then
  pass T09 "审计日志" "total=$ATOTAL"
else
  fail T09 "审计日志" "total=${ATOTAL:-N/A} body=$(trunc "$BODY")"
fi

# ---------- T10 越权: viewer 调 IAM 用户管理 ----------
req POST /api/auth/login '{"username":"viewer","password":"Viewer@123"}' ""
VTOKEN="$(jfind "$BODY" accessToken)"
[ -z "$VTOKEN" ] && VTOKEN="$(jfind "$BODY" token)"
USR="$(curl -s -w $'\n%{http_code}' -H "Authorization: Bearer $VTOKEN" "$BASE/api/iam/users" 2>/dev/null)"
UHTTP="${USR##*$'\n'}"
UBODY="$(printf '%s' "$USR" | sed '$d')"
UCODE="$(jfind "$UBODY" code)"
if [ "$UCODE" = "6" ] || [ "$UHTTP" = "403" ]; then
  pass T10 "viewer 越权拦截" "code=$UCODE http=$UHTTP (期望 code=6 或 403)"
else
  fail T10 "viewer 越权拦截" "code=${UCODE:-N/A} http=${UHTTP:-N/A} token=${VTOKEN:+获取成功}"
fi

# ---------- 汇总 ----------
say "==================================================="
say "冒烟测试汇总: PASS=$PASS FAIL=$FAIL (共 $((PASS+FAIL)) 项)"
if [ "$FAIL" -gt 0 ]; then
  exit 1
fi
exit 0
