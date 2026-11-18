#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

KEYSTORE_FILE="${1:-litetube-release.jks}"
KEY_ALIAS="${2:-litetube}"
PROPS_FILE="keystore.properties"
B64_FILE="${KEYSTORE_FILE}.b64"

if ! command -v keytool >/dev/null 2>&1; then
  echo "Lỗi: Không tìm thấy 'keytool'. Hãy cài đặt JDK (OpenJDK 17+)." >&2
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "Lỗi: Không tìm thấy 'python3' để tạo keystore.properties an toàn." >&2
  exit 1
fi

if [[ -e "$KEYSTORE_FILE" ]]; then
  echo "Lỗi: File '$KEYSTORE_FILE' đã tồn tại. Không ghi đè để tránh mất khóa ký cũ!" >&2
  exit 1
fi

if [[ -e "$PROPS_FILE" ]]; then
  echo "Lỗi: File '$PROPS_FILE' đã tồn tại. Vui lòng kiểm tra trước khi tạo mới." >&2
  exit 1
fi

echo "=== Tạo Release Keystore cho LiteTube (com.kaz.litetube) ==="
echo "File đầu ra: $KEYSTORE_FILE | Alias: $KEY_ALIAS"
echo

read -r -s -p "Nhập mật khẩu Keystore (tối thiểu 6 ký tự): " STORE_PASS
echo
if [[ ${#STORE_PASS} -lt 6 ]]; then
  echo "Lỗi: Mật khẩu phải có ít nhất 6 ký tự." >&2
  exit 1
fi

read -r -s -p "Nhập lại mật khẩu Keystore: " STORE_PASS_CONFIRM
echo
if [[ "$STORE_PASS" != "$STORE_PASS_CONFIRM" ]]; then
  echo "Lỗi: Mật khẩu xác nhận không khớp." >&2
  exit 1
fi

unset STORE_PASS_CONFIRM

STORE_PASS="$STORE_PASS" keytool -genkeypair -v \
  -storetype PKCS12 \
  -keystore "$KEYSTORE_FILE" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias "$KEY_ALIAS" \
  -storepass:env STORE_PASS \
  -keypass:env STORE_PASS \
  -dname "CN=Kaz, OU=Mobile, O=Kaz, L=VN, S=VN, C=VN"

chmod 600 "$KEYSTORE_FILE"

KEYSTORE_FILE="$KEYSTORE_FILE" KEY_ALIAS="$KEY_ALIAS" STORE_PASS="$STORE_PASS" PROPS_FILE="$PROPS_FILE" \
  python3 - <<'PY'
import os

def escape_prop(val: str) -> str:
    return val.replace("\\", "\\\\")

lines = [
    f"storeFile={escape_prop(os.environ['KEYSTORE_FILE'])}",
    f"storePassword={escape_prop(os.environ['STORE_PASS'])}",
    f"keyAlias={escape_prop(os.environ['KEY_ALIAS'])}",
    f"keyPassword={escape_prop(os.environ['STORE_PASS'])}",
]
with open(os.environ["PROPS_FILE"], "w", encoding="utf-8") as f:
    f.write("\n".join(lines) + "\n")
PY
chmod 600 "$PROPS_FILE"

if base64 --help 2>&1 | grep -q -- "-w"; then
  base64 -w 0 "$KEYSTORE_FILE" > "$B64_FILE"
else
  base64 < "$KEYSTORE_FILE" | tr -d '\n' > "$B64_FILE"
fi
chmod 600 "$B64_FILE"

unset STORE_PASS

echo
echo "Hoàn tất! Đã tạo:"
echo "  - $KEYSTORE_FILE (file khóa ký Release - hãy sao lưu an toàn, KHÔNG commit lên Git)"
echo "  - $PROPS_FILE (cấu hình build Release ở local, đã được .gitignore bỏ qua)"
echo "  - $B64_FILE (chuỗi Base64 1 dòng để dán vào GitHub Secrets)"
echo
echo "=== Hướng dẫn nạp vào GitHub Actions Secrets ==="
echo "Vào GitHub Repo -> Settings -> Secrets and variables -> Actions -> New repository secret:"
echo "  1. KEYSTORE_BASE64   : toàn bộ nội dung file '$B64_FILE'"
echo "  2. KEYSTORE_PASSWORD : mật khẩu bạn vừa nhập"
echo "  3. KEY_ALIAS         : $KEY_ALIAS"
echo "  4. KEY_PASSWORD      : mật khẩu bạn vừa nhập"
echo
echo "Sau khi lưu xong trên GitHub, bạn có thể xóa file '$B64_FILE' ở máy nếu muốn."
