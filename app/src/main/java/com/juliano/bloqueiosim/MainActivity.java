package com.juliano.bloqueiosim;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int REQ_ROLE = 1;
    private static final int REQ_PERMS = 2;

    private LinearLayout root;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        sv.addView(root);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        root.removeAllViews();
        SharedPreferences p = Prefs.get(this);

        title("Bloqueio por SIM");
        text("Bloqueia ligações de números que NÃO estão nos seus contatos, só no(s) chip(s) que você escolher.");

        // 1. Permissões
        boolean perms = hasPerms();
        boolean role = hasRole();
        header("1. Configuração");
        status("Permissões (contatos e telefone)", perms);
        if (!perms) button("Conceder permissões", v -> requestPermissions(
                new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.READ_PHONE_STATE}, REQ_PERMS));
        status("App definido como filtro de chamadas", role);
        if (!role) button("Definir como filtro de chamadas", v -> requestRole());

        // 2. Liga/desliga
        header("2. Bloqueio");
        Switch sw = new Switch(this);
        sw.setText("Bloqueio ativado");
        sw.setTextSize(17);
        sw.setChecked(p.getBoolean(Prefs.KEY_ENABLED, false));
        sw.setOnCheckedChangeListener((x, on) -> p.edit().putBoolean(Prefs.KEY_ENABLED, on).apply());
        root.addView(sw);

        CheckBox hidden = new CheckBox(this);
        hidden.setText("Bloquear também números ocultos/privados");
        hidden.setChecked(p.getBoolean(Prefs.KEY_BLOCK_HIDDEN, true));
        hidden.setOnCheckedChangeListener((x, on) -> p.edit().putBoolean(Prefs.KEY_BLOCK_HIDDEN, on).apply());
        root.addView(hidden);

        // 3. Escolha dos chips
        header("3. Em quais chips bloquear?");
        if (!perms) {
            text("Conceda as permissões para ver os chips.");
        } else {
            List<PhoneAccountHandle> sims = listSims();
            if (sims.isEmpty()) text("Nenhum chip encontrado.");
            Set<String> chosen = Prefs.blockedSims(this);
            int n = 1;
            for (PhoneAccountHandle h : sims) {
                CheckBox cb = new CheckBox(this);
                cb.setText("SIM " + n++ + " — " + label(h));
                cb.setTextSize(16);
                cb.setChecked(chosen.contains(Prefs.key(h)));
                cb.setOnCheckedChangeListener((x, on) -> Prefs.setSimBlocked(this, h, on));
                root.addView(cb);
            }
        }

        // 4. Histórico
        header("Últimas chamadas bloqueadas");
        String log = p.getString(Prefs.KEY_LOG, "");
        text(log.isEmpty() ? "Nenhuma ainda." : log);
        if (!log.isEmpty()) button("Limpar histórico", v -> {
            p.edit().remove(Prefs.KEY_LOG).apply();
            build();
        });
    }

    // ---------- helpers ----------

    private boolean hasPerms() {
        return checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasRole() {
        RoleManager rm = getSystemService(RoleManager.class);
        return rm != null && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
    }

    private void requestRole() {
        RoleManager rm = getSystemService(RoleManager.class);
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQ_ROLE);
        }
    }

    @SuppressWarnings("MissingPermission")
    private List<PhoneAccountHandle> listSims() {
        TelecomManager tm = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        try {
            return tm.getCallCapablePhoneAccounts();
        } catch (SecurityException e) {
            return java.util.Collections.emptyList();
        }
    }

    private String label(PhoneAccountHandle h) {
        TelecomManager tm = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        PhoneAccount a = tm.getPhoneAccount(h);
        return a != null && a.getLabel() != null ? a.getLabel().toString() : "Chip";
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) { build(); }

    @Override
    protected void onActivityResult(int req, int res, android.content.Intent d) { build(); }

    private void title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(24);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(t);
    }

    private void header(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(22), 0, dp(6));
        root.addView(t);
    }

    private void text(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(15);
        t.setPadding(0, dp(4), 0, dp(4));
        root.addView(t);
    }

    private void status(String s, boolean ok) {
        text((ok ? "✅ " : "❌ ") + s);
    }

    private void button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setOnClickListener(l);
        root.addView(b);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
