# دمج رفيقي داخل حصن (عميق)

## ما تم تنفيذه

### 1. أصول محلية (Offline PWA)
- المجلد: `app/src/main/assets/rafiqi/`
- تُخدم عبر `https://rafiqi.hisn.local/` باستخدام `WebViewAssetLoader` (بدون `file://`).
- حالياً يوجد `index.html` توضيحي. ضع ناتج `npm run build` من مشروع رفيقي هنا.

### 2. إشعارات الصلاة
- `PrayerScheduler` + `PrayerAlarmReceiver` + قناة إشعارات عالية الأولوية.
- أذونات: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `WAKE_LOCK`.
- الجسر: `window.HisnRafiqi` (يُضاف فقط على الأصل المحلي).

### 3. واجهة المستخدم
- بطاقة «رفيقي» في الصفحة الرئيسية (غير الخاصة).
- كتابة «رفيقي» أو «rafiqi» في شريط العنوان تفتح النسخة المحلية.

### 4. الحماية
- الجسر لا يُعرَّض لأي موقع آخر.
- حظر الإعلانات لا يطبَّق على رفيقي (محلي أو `rafiqi.iegy.net`).

## كيفية ملء الأصول

```bash
git clone https://github.com/iegy/rafiqi.git
cd rafiqi
npm ci
npm run fetch:assets   # إن لزم
npm run build
cp -r dist/* /path/to/Hisn/app/src/main/assets/rafiqi/
```

ثم أعد بناء APK حصن.

## استخدام الجسر من JavaScript (داخل رفيقي المحلي)

```js
// بعد تحميل الصفحة (الجسر جاهز)
HisnRafiqi.schedule([
  { name: "Fajr",  label: "الفجر",  at: Date.parse("2026-10-23T04:30:00") },
  { name: "Dhuhr", label: "الظهر",  at: Date.parse("2026-10-23T12:15:00") },
  // ...
], "القاهرة");

HisnRafiqi.cancelAllPrayers();
HisnRafiqi.hasNotificationPermission(); // boolean
HisnRafiqi.canScheduleExactAlarms();    // boolean
```

يُفضَّل أن يحسب رفيقي المواقيت كما يفعل الآن، ثم يستدعي `schedule` مرة يومياً (أو عند تغيير المدينة).

## ملاحظات

- على Android 12+ قد يطلب النظام إذن «المنبهات الدقيقة» من الإعدادات.
- على Android 13+ يُطلب إذن الإشعارات عند أول استخدام (يمكن إضافة طلب صريح لاحقاً من Activity).
- النسخة الحالية من `index.html` المحلية هي placeholder؛ بعد نسخ `dist/` ستعمل الواجهة الكاملة offline.
- لو أردت فتح النسخة أونلاين مع الجسر أيضاً، يمكن توسيع الشرط في `onPageStarted` ليشمل `rafiqi.iegy.net` (مع الحذر الأمني).
