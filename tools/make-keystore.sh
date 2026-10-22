#!/usr/bin/env bash
# يولّد مفتاح التوقيع الثابت مرة واحدة فقط، ويطبع القيم الأربع التي تضعها في GitHub Secrets.
# شغّله على جهازك (يحتاج JDK)، واحتفظ بملف hisn-release.jks في مكان آمن: لو ضاع لن تستطيع تحديث التطبيق المثبّت.
set -euo pipefail

KEYSTORE="${1:-hisn-release.jks}"
ALIAS="${2:-hisn}"
read -r -s -p "اختر كلمة مرور للمفتاح (6 أحرف أو أكثر): " PASS; echo

keytool -genkeypair -v -keystore "$KEYSTORE" -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -validity 36500 \
  -storepass "$PASS" -keypass "$PASS" \
  -dname "CN=HISN Browser, O=iegy.net, C=EG"

echo
echo "أضف هذه الأربع في GitHub: Settings > Secrets and variables > Actions > New repository secret"
echo "  ANDROID_KEYSTORE_BASE64 = (القيمة الطويلة أدناه)"
base64 -w0 "$KEYSTORE" 2>/dev/null || base64 "$KEYSTORE" | tr -d '\n'
echo
echo "  KEYSTORE_PASSWORD       = كلمة المرور التي كتبتها"
echo "  KEY_ALIAS               = $ALIAS"
echo "  KEY_PASSWORD            = كلمة المرور نفسها"
