#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_NAME="doc-image-stripper"
VERSION="0.0.1-SNAPSHOT"
DIST_DIR="${ROOT_DIR}/dist"
PACKAGE_DIR="${DIST_DIR}/${APP_NAME}-windows"
ZIP_PATH="${DIST_DIR}/${APP_NAME}-windows.zip"
JAR_SOURCE="${ROOT_DIR}/target/${APP_NAME}-${VERSION}.jar"

cd "${ROOT_DIR}"

if ! command -v mvn >/dev/null 2>&1; then
    echo "Maven is not installed or not available in PATH."
    exit 1
fi

if ! command -v zip >/dev/null 2>&1; then
    echo "zip is not installed or not available in PATH."
    exit 1
fi

echo "Building ${APP_NAME}..."
mvn -q package

rm -rf "${PACKAGE_DIR}" "${ZIP_PATH}"
mkdir -p "${PACKAGE_DIR}"

cp "${JAR_SOURCE}" "${PACKAGE_DIR}/${APP_NAME}.jar"
cp "${ROOT_DIR}/src/main/resources/static/index.html" "${PACKAGE_DIR}/frontend.html"

cat > "${PACKAGE_DIR}/run-windows.bat" <<'BAT'
@echo off
setlocal
cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 (
    echo Java was not found. Install Java 8 or newer, then run this file again.
    pause
    exit /b 1
)

echo Starting Doc Image Stripper...
echo Frontend page: http://localhost:8080/
echo.
start "" cmd /c "timeout /t 3 /nobreak >nul && start http://localhost:8080/"
java -jar doc-image-stripper.jar
pause
BAT

cat > "${PACKAGE_DIR}/README-WINDOWS.txt" <<'TXT'
Doc Image Stripper - Windows package

Recommended way:
1. Install Java 8 or newer on the Windows computer.
2. Double-click run-windows.bat.
3. The browser should open http://localhost:8080/.
4. Upload or drag a PDF on the page.

Manual way:
1. Open PowerShell or Command Prompt in this folder.
2. Run:
   java -jar doc-image-stripper.jar
3. Open:
   http://localhost:8080/

About frontend.html:
- The jar already contains the frontend page, so http://localhost:8080/ is the most reliable entry.
- frontend.html is also included for convenience. If you double-click it, keep the jar running first.
- If port 8080 is already occupied, use:
   java -jar doc-image-stripper.jar --server.port=8081
  Then open http://localhost:8081/ instead.
TXT

(
    cd "${DIST_DIR}"
    zip -qr "${APP_NAME}-windows.zip" "${APP_NAME}-windows"
)

echo "Created: ${ZIP_PATH}"
