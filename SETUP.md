# Android setup (Capacitor)

1. Copy `KeepAlivePlugin.java` + `KeepAliveService.java` into `android/app/src/main/java/<your/package>/` and fix the `package` line.
2. Copy `order_alert.wav` to `android/app/src/main/res/raw/`.
3. In `MainActivity.java` add, before `super.onCreate(...)`: `registerPlugin(KeepAlivePlugin.class);`
4. In `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.VIBRATE"/>
<uses-permission android:name="android.permission.WAKE_LOCK"/>
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>
<!-- inside <application> -->
<service android:name=".KeepAliveService" android:exported="false" android:foregroundServiceType="specialUse">
  <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="Receives restaurant orders"/>
</service>
```
5. `npm i @capacitor/share @capacitor/local-notifications && npx cap sync android`, then replace the app's web `index.html` with the new one.
6. Order push (Cloud Function): send FCM with `android: { priority: "high", notification: { channelId: "orders_alarm", sound: "order_alert" } }`.

# Customer app
Treat the shop as open only if the admin is alive:
```js
const shopOpen = () => pres.open && Date.now() - (pres.hb || 0) < 100000;
setInterval(updateShopStatus, 15000); // re-check even without new snapshots
```
Use `shopOpen()` wherever the customer app currently reads `pres.open`.
