package com.juliano.bloqueiosim;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Verifica no GitHub se há versão nova, baixa e instala. */
final class Updater {
    static final String REPO = "julianocz/BloqueioSIM";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";

    /** silent = true: só avisa se houver versão nova (usado ao abrir o app). */
    static void check(Activity a, boolean silent) {
        if (!silent) toast(a, "Verificando atualização...");
        new Thread(() -> {
            try {
                JSONObject rel = new JSONObject(readText(API));
                int latest = Integer.parseInt(rel.getString("tag_name").replaceAll("[^0-9]", ""));
                String url = null;
                JSONArray assets = rel.getJSONArray("assets");
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject as = assets.getJSONObject(i);
                    if (as.getString("name").endsWith(".apk")) url = as.getString("browser_download_url");
                }
                if (latest <= BuildConfig.VERSION_CODE || url == null) {
                    if (!silent) toast(a, "Você já tem a versão mais nova (" + BuildConfig.VERSION_NAME + ")");
                    return;
                }
                final String apkUrl = url;
                a.runOnUiThread(() -> {
                    if (a.isFinishing()) return;
                    new AlertDialog.Builder(a)
                            .setTitle("Atualização disponível")
                            .setMessage("Nova versão 2." + latest + " (você tem " + BuildConfig.VERSION_NAME + "). Atualizar agora?")
                            .setPositiveButton("Atualizar", (d, w) -> download(a, apkUrl))
                            .setNegativeButton("Depois", null)
                            .show();
                });
            } catch (Throwable e) {
                if (!silent) toast(a, "Não foi possível verificar: " + e.getMessage());
            }
        }).start();
    }

    private static void download(Activity a, String url) {
        if (!a.getPackageManager().canRequestPackageInstalls()) {
            toast(a, "Permita \"instalar apps\" para o Bloqueio por SIM e toque em Atualizar de novo");
            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + a.getPackageName())));
            return;
        }
        toast(a, "Baixando atualização...");
        new Thread(() -> {
            try {
                File f = new File(a.getCacheDir(), "update.apk");
                HttpURLConnection c = open(url);
                try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(f)) {
                    copy(in, out);
                }
                install(a, f);
            } catch (Throwable e) {
                toast(a, "Erro ao baixar: " + e.getMessage());
            }
        }).start();
    }

    private static void install(Activity a, File f) throws Exception {
        PackageInstaller pi = a.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int id = pi.createSession(p);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (InputStream in = new FileInputStream(f); OutputStream out = s.openWrite("app", 0, f.length())) {
                copy(in, out);
                s.fsync(out);
            }
            Intent i = new Intent(a, InstallReceiver.class);
            PendingIntent pend = PendingIntent.getBroadcast(a, id, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            s.commit(pend.getIntentSender());
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "BloqueioSIM");
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        int code = c.getResponseCode();
        if (code == 404) throw new Exception("nenhuma versão publicada (o repositório é público?)");
        if (code >= 400) throw new Exception("HTTP " + code);
        return c;
    }

    private static String readText(String url) throws Exception {
        HttpURLConnection c = open(url);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (InputStream in = c.getInputStream()) { copy(in, b); }
        return b.toString("UTF-8");
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    private static void toast(Activity a, String s) {
        a.runOnUiThread(() -> Toast.makeText(a, s, Toast.LENGTH_LONG).show());
    }

    private Updater() {}
}
