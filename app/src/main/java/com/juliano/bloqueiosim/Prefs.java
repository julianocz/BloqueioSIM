package com.juliano.bloqueiosim;

import android.content.Context;
import android.content.SharedPreferences;
import android.telecom.PhoneAccountHandle;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Configurações salvas do app. */
final class Prefs {
    private static final String FILE = "config";
    static final String KEY_ENABLED = "ativo";
    static final String KEY_SIMS = "sims_bloqueados";
    static final String KEY_BLOCK_HIDDEN = "bloquear_ocultos";
    static final String KEY_LOG = "historico";

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Identificador estável de um chip (conta telefônica). */
    static String key(PhoneAccountHandle h) {
        return h.getComponentName().flattenToString() + "|" + h.getId();
    }

    static Set<String> blockedSims(Context c) {
        return new HashSet<>(get(c).getStringSet(KEY_SIMS, new HashSet<>()));
    }

    static void setSimBlocked(Context c, PhoneAccountHandle h, boolean on) {
        Set<String> s = blockedSims(c);
        if (on) s.add(key(h)); else s.remove(key(h));
        get(c).edit().putStringSet(KEY_SIMS, s).apply();
    }

    static void addLog(Context c, String number, String sim) {
        String time = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(new Date());
        String line = time + "  " + number + "  (" + sim + ")";
        String old = get(c).getString(KEY_LOG, "");
        String[] lines = old.isEmpty() ? new String[0] : old.split("\n");
        StringBuilder sb = new StringBuilder(line);
        for (int i = 0; i < Math.min(lines.length, 29); i++) sb.append("\n").append(lines[i]);
        get(c).edit().putString(KEY_LOG, sb.toString()).apply();
    }

    private Prefs() {}
}
