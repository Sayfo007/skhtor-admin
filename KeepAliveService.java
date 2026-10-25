package com.skhtor.admin; // change to your app id

import android.app.*;
import android.content.Intent;
import android.os.*;

public class KeepAliveService extends Service {
  @Override public IBinder onBind(Intent i) { return null; }
  @Override public int onStartCommand(Intent i, int f, int id) {
    if (Build.VERSION.SDK_INT >= 26)
      getSystemService(NotificationManager.class).createNotificationChannel(
        new NotificationChannel("keepalive", "Shop online", NotificationManager.IMPORTANCE_LOW));
    PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "keepalive") : new Notification.Builder(this);
    b.setContentTitle("Skhtor Admin is running").setContentText("Receiving orders")
      .setSmallIcon(getApplicationInfo().icon).setContentIntent(pi).setOngoing(true);
    startForeground(1, b.build());
    return START_STICKY;
  }
}
