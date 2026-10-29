package com.skhtor.admin; // change to your app id

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import com.getcapacitor.*;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "KeepAlive")
public class KeepAlivePlugin extends Plugin {
  static MediaPlayer mp;
  AudioAttributes alarmAttrs() {
    return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
  }
  // sound = name of a file in res/raw (a-z, 0-9, _). Falls back to the default alarm.
  String snd(PluginCall c) {
    String s = c == null ? null : c.getString("sound", "order_alert");
    if (s == null || !s.matches("[a-z0-9_]+")) s = "order_alert";
    Context x = getContext();
    if (x.getResources().getIdentifier(s, "raw", x.getPackageName()) == 0) s = "order_alert";
    return s;
  }
  Uri sound(String name) { return Uri.parse("android.resource://" + getContext().getPackageName() + "/raw/" + name); }
  String chanId(String name) { return name.equals("order_alert") ? "orders_alarm" : "orders_alarm_" + name; }

  @PluginMethod public void createChannel(PluginCall c) {
    if (Build.VERSION.SDK_INT >= 26) {
      String name = snd(c), id = chanId(name);
      NotificationManager nm = getContext().getSystemService(NotificationManager.class);
      // keep the default channel; remove channels of other chosen sounds
      for (NotificationChannel old : nm.getNotificationChannels()) {
        String oid = old.getId();
        if (oid.startsWith("orders_alarm_") && !oid.equals(id)) nm.deleteNotificationChannel(oid);
      }
      NotificationChannel ch = new NotificationChannel(id, "New orders (alarm)", NotificationManager.IMPORTANCE_HIGH);
      ch.setSound(sound(name), alarmAttrs());      // alarm usage = plays even in silent mode
      ch.enableVibration(true);
      ch.setVibrationPattern(new long[]{0, 800, 300, 800, 300, 800, 300, 800});
      ch.setBypassDnd(true);
      ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
      nm.createNotificationChannel(ch);
      if (!id.equals("orders_alarm") && nm.getNotificationChannel("orders_alarm") == null) {
        NotificationChannel d = new NotificationChannel("orders_alarm", "New orders (alarm)", NotificationManager.IMPORTANCE_HIGH);
        d.setSound(sound("order_alert"), alarmAttrs()); d.enableVibration(true);
        d.setVibrationPattern(new long[]{0, 800, 300, 800, 300, 800, 300, 800});
        d.setBypassDnd(true); d.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(d);
      }
    }
    c.resolve();
  }
  @PluginMethod public void start(PluginCall c) {
    Context x = getContext(); Intent i = new Intent(x, KeepAliveService.class);
    if (Build.VERSION.SDK_INT >= 26) x.startForegroundService(i); else x.startService(i);
    c.resolve();
  }
  @PluginMethod public void stop(PluginCall c) {
    getContext().stopService(new Intent(getContext(), KeepAliveService.class)); c.resolve();
  }
  @PluginMethod public void batteryStatus(PluginCall c) {
    PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
    JSObject r = new JSObject();
    r.put("ignoring", pm.isIgnoringBatteryOptimizations(getContext().getPackageName()));
    c.resolve(r);
  }
  @PluginMethod public void requestBattery(PluginCall c) {
    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getContext().getPackageName()));
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); getContext().startActivity(i); c.resolve();
  }
  @PluginMethod public void ring(PluginCall c) {
    try {
      Context x = getContext();
      AudioManager am = (AudioManager) x.getSystemService(Context.AUDIO_SERVICE);
      try { am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0); } catch (Exception e) {}
      stopSound();
      mp = new MediaPlayer(); mp.setAudioAttributes(alarmAttrs());
      mp.setDataSource(x, sound(snd(c))); mp.setVolume(1f, 1f); mp.prepare(); mp.start();
      Vibrator v = (Vibrator) x.getSystemService(Context.VIBRATOR_SERVICE);
      v.vibrate(new long[]{0, 800, 300, 800, 300, 800, 300, 800, 300, 800}, -1);
      c.resolve();
    } catch (Exception e) { c.reject(e.getMessage()); }
  }
  static void stopSound() { try { if (mp != null) { mp.stop(); mp.release(); } } catch (Exception e) {} mp = null; }
  @PluginMethod public void stopRing(PluginCall c) {
    stopSound(); ((Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE)).cancel(); c.resolve();
  }
}
