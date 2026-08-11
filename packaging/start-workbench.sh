#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

if [[ -x ./ECM-Dev-Workbench ]]; then
  exec ./ECM-Dev-Workbench
fi

if ! command -v java >/dev/null 2>&1; then
  echo "Java 17 or later is required to run ECM-Dev-Workbench."
  echo "Install a JDK and try again."
  exit 1
fi

if [[ ! -f ./ECM-Dev-Workbench.jar ]]; then
  echo "ECM-Dev-Workbench.jar was not found in this folder."
  exit 1
fi

echo "Starting ECM-Dev-Workbench on http://127.0.0.1:18080 ..."
exec java -Dworkbench.desktop=true -Dworkbench.open-browser=true -jar "./ECM-Dev-Workbench.jar"
