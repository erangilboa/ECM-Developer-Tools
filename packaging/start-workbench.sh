#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

if [[ -x ./DCTMWorkbench ]]; then
  exec ./DCTMWorkbench
fi

if ! command -v java >/dev/null 2>&1; then
  echo "Java 17 or later is required to run DCTM Workbench."
  echo "Install a JDK and try again."
  exit 1
fi

echo "Starting DCTM Workbench on http://127.0.0.1:18080 ..."
exec java -Dworkbench.desktop=true -Dworkbench.open-browser=true -jar "./dctm-workbench.jar"
