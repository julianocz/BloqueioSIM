package com.juliano.bloqueiosim;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

/** Captura qualquer falha do app e abre a tela de erro (em processo separado). */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                String trace = Log.getStackTraceString(e);
                Prefs.get(this).edit().putString("ultimo_erro", trace).commit();
                Intent i = new Intent(this, ErrorActivity.class);
                i.putExtra("erro", trace);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
            } catch (Throwable ignored) {}
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        });
    }
}
