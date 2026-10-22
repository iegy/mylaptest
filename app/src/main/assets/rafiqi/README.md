# رفيقي — أصول محلية داخل حصن

ضع هنا ناتج بناء مشروع رفيقي (`dist/` بعد `npm run build`).

## الخطوات

```bash
git clone https://github.com/iegy/rafiqi.git
cd rafiqi
npm ci
npm run fetch:assets   # إن لزم
npm run build
# انسخ محتويات dist/* إلى هذا المجلد (assets/rafiqi/)
cp -r dist/* /path/to/Hisn/app/src/main/assets/rafiqi/
```

بعد النسخ، أعد بناء APK حصن. الصفحة المحلية ستُفتح عبر:

`https://rafiqi.hisn.local/`

الجسر الأصلي متاح في الصفحة:

```js
window.HisnRafiqi.schedulePrayers(JSON.stringify({
  city: "Cairo",
  prayers: [
    { name: "Fajr", label: "الفجر", at: Date.now() + 3600000 }
  ]
}))
```

انظر `RafiqiBridge.kt` و `PrayerScheduler.kt` للتفاصيل.
