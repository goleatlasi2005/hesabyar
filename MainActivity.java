package ir.hesabyar.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.content.Context;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private WebView web;

    private static final int REQ_CREATE_BACKUP = 1001;
    private static final int REQ_RESTORE_BACKUP = 1002;

    private String pendingBackupJson = "";

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                "installments",
                "یادآوری اقساط",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            getSystemService(NotificationManager.class)
                .createNotificationChannel(ch);
        }

        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());

        web.addJavascriptInterface(new AppBridge(this), "Hesabyar");

        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        Uri uri = data.getData();
        if (uri == null) return;

        try {
            if (requestCode == REQ_CREATE_BACKUP) {

                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new Exception("Cannot write file");

                    out.write(pendingBackupJson.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }

                Toast.makeText(this,
                    "نسخه پشتیبان با موفقیت ذخیره شد",
                    Toast.LENGTH_LONG).show();

            } else if (requestCode == REQ_RESTORE_BACKUP) {

                String json;

                try (InputStream in = getContentResolver().openInputStream(uri);
                     ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

                    if (in == null) throw new Exception("Cannot read file");

                    byte[] bytes = new byte[4096];
                    int count;

                    while ((count = in.read(bytes)) != -1) {
                        buffer.write(bytes, 0, count);
                    }

                    json = buffer.toString("UTF-8");
                }

                final String safeJson = JSONObject.quote(json);

                runOnUiThread(() -> {
                    web.evaluateJavascript(
                        "window.restoreBackupFromAndroid(" + safeJson + ")",
                        null
                    );
                });
            }

        } catch (Exception e) {
            Toast.makeText(this,
                "خطا در انجام عملیات فایل",
                Toast.LENGTH_LONG).show();
        }
    }

    public static class AppBridge {

        private final MainActivity a;

        AppBridge(MainActivity activity) {
            a = activity;
        }

        @JavascriptInterface
        public void exportBackup(String json) {

            a.pendingBackupJson = json;

            a.runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    intent.putExtra(
                        Intent.EXTRA_TITLE,
                        "hesabyar-backup-" + System.currentTimeMillis() + ".json"
                    );

                    a.startActivityForResult(intent, REQ_CREATE_BACKUP);

                } catch (Exception e) {
                    Toast.makeText(a,
                        "امکان ذخیره فایل وجود ندارد",
                        Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void importBackup() {

            a.runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");

                    a.startActivityForResult(intent, REQ_RESTORE_BACKUP);

                } catch (Exception e) {
                    Toast.makeText(a,
                        "امکان انتخاب فایل وجود ندارد",
                        Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void notify(String title, String message) {

            NotificationManager nm =
                (NotificationManager) a.getSystemService(Context.NOTIFICATION_SERVICE);

            Intent intent = new Intent(a, MainActivity.class);

            PendingIntent pi = PendingIntent.getActivity(
                a,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            Notification.Builder b =
                Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(a, "installments")
                : new Notification.Builder(a);

            b.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(pi);

            nm.notify((int) System.currentTimeMillis(), b.build());
        }
    }
}
