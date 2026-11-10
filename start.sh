#!/usr/bin/env bash
# Build the static project into PROJECT_DIR/dist and serve it in the foreground on PORT (default 3000).
set -euo pipefail

cd "$(dirname "$0")"
PROJECT_DIR="$(pwd)"
: "${PORT:=3000}"
export PORT

# Worker metadata / capture evidence directories (never source or built output).
: "${OPENCODE_WEB_DIR:=/home/runner/work/_temp/omgithub-web}"
: "${RUNNER_TEMP:=/home/runner/work/_temp}"

echo "[start] PROJECT_DIR=$PROJECT_DIR PORT=$PORT OPENCODE_WEB_DIR=$OPENCODE_WEB_DIR"

# ---- install dependencies / build when needed ----
if [[ -f "$PROJECT_DIR/package.json" ]]; then
  echo "[start] package.json found; installing dependencies"
  /usr/bin/time -p npm install --no-audit --no-fund
fi

# ---- build: static deployment output lives inside PROJECT_DIR/dist ----
/usr/bin/time -p rm -rf "$PROJECT_DIR/dist"
/usr/bin/time -p mkdir -p "$PROJECT_DIR/dist"
/usr/bin/time -p cp -a "$PROJECT_DIR/app/." "$PROJECT_DIR/dist/"
if [[ ! -f "$PROJECT_DIR/dist/index.html" ]]; then
  echo "[start] build produced no dist/index.html" >&2
  exit 1
fi
echo "[start] built static output at $PROJECT_DIR/dist"

# ---- worker metadata: deployment output manifest ----
/usr/bin/time -p mkdir -p "$OPENCODE_WEB_DIR"
/usr/bin/time -p /usr/bin/printf '%s' \
  "{\"project\":\"${PROJECT_DIR}\",\"directory\":\"${PROJECT_DIR}/dist\"}" \
  > "$OPENCODE_WEB_DIR/deployment-output.json"
echo "[start] wrote $OPENCODE_WEB_DIR/deployment-output.json"

# ---- serve in the foreground ----
echo "[start] serving http://0.0.0.0:${PORT}/ (document root $PROJECT_DIR/dist)"
/usr/bin/time -p python3 -m http.server "${PORT}" --directory "$PROJECT_DIR/dist" --bind 0.0.0.0
