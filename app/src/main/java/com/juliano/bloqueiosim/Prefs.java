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
    static final String KEY_DDD = "ddd_liberados|";
    static final String KEY_NUMS = "numeros_liberados|";

    /** Só dígitos. */
    static String digits(String s) {
        return s == null ? "" : s.replaceAll("[^0-9]", "");
    }

    /**
     * Converte para o formato nacional brasileiro: DDD + número (10 ou 11 dígitos).
     * Aceita +55..., 0xx..., 0 + operadora + DDD..., ou número sem DDD (retorna como está).
     */
    static String national(String raw) {
        String d = digits(raw);
        if (d.startsWith("55") && d.length() >= 12) d = d.substring(2);
        if (d.startsWith("0")) {
            if (d.length() >= 13) d = d.substring(3);      // 0 + operadora (2) + DDD + número
            else if (d.length() >= 11) d = d.substring(1); // 0 + DDD + número
        }
        return d;
    }

    /** DDD do número, ou "" se a ligação veio sem DDD. */
    static String ddd(String raw) {
        String n = national(raw);
        return (n.length() == 10 || n.length() == 11) ? n.substring(0, 2) : "";
    }

    /** true se o número está liberado nas exceções deste chip. */
    static boolean isException(Context c, PhoneAccountHandle h, String number) {
        String k = key(h);
        SharedPreferences p = get(c);

        String dddCall = ddd(number);
        if (!dddCall.isEmpty()) {
            for (String item : p.getString(KEY_DDD + k, "").split("[,;\\s]+")) {
                String dd = digits(item).replaceFirst("^0+", "");
                if (!dd.isEmpty() && dd.equals(dddCall)) return true;
            }
        }

        String call = national(number);
        if (call.length() < 8) return false;
        for (String item : p.getString(KEY_NUMS + k, "").split("[\\n,;]+")) {
            String n = national(item);
            if (n.length() < 8) continue;
            if (n.equals(call)) return true;
            // um dos dois sem DDD: compara o final
            if ((n.length() <= 9 || call.length() <= 9) && (n.endsWith(call) || call.endsWith(n))) return true;
        }
        return false;
    }

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

    // ---------- DDDs liberados por chip ----------

    /** DDDs liberados para um chip, ex: {"31","41"}. */
    static Set<String> allowedDdds(Context c, PhoneAccountHandle h) {
        return parseDdds(get(c).getString("ddd|" + key(h), ""));
    }

    static void setAllowedDdds(Context c, PhoneAccountHandle h, String text) {
        get(c).edit().putString("ddd|" + key(h), joinDdds(parseDdds(text))).apply();
    }

    /** Aceita "031, 41 / 11" e devolve {"31","41","11"}. */
    static Set<String> parseDdds(String text) {
        Set<String> out = new java.util.TreeSet<>();
        if (text == null) return out;
        for (String part : text.split("[^0-9]+")) {
            String d = part.replaceFirst("^0+", "");
            if (d.length() == 2) out.add(d);
        }
        return out;
    }

    static String joinDdds(Set<String> ddds) {
        StringBuilder sb = new StringBuilder();
        for (String d : ddds) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(d);
        }
        return sb.toString();
    }

    /**
     * Descobre o DDD de um número brasileiro.
     * Ex: +5531999998888, 031999998888, 0 15 31 99999-8888, 31999998888.
     * Retorna null se não der para saber (ex: ligação local sem DDD).
     */
    static String extractDdd(String number) {
        if (number == null) return null;
        String n = number.replaceAll("[^0-9]", "");
        if (number.trim().startsWith("+")) {
            if (n.startsWith("55") && (n.length() == 12 || n.length() == 13)) return n.substring(2, 4);
            return null; // internacional
        }
        if (n.startsWith("55") && (n.length() == 12 || n.length() == 13)) return n.substring(2, 4);
        if (n.startsWith("0")) {
            if (n.length() == 11 || n.length() == 12) return n.substring(1, 3);      // 0 + DDD + número
            if (n.length() == 13 || n.length() == 14) return n.substring(3, 5);      // 0 + operadora + DDD + número
            return null;
        }
        if (n.length() == 10 || n.length() == 11) return n.substring(0, 2);          // DDD + número
        return null;
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
