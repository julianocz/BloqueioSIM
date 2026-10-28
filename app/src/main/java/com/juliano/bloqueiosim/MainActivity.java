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
import android.widget.EditText;
import android.widget.Toast;
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
        if (b == null) Updater.check(this, true); // procura versão nova ao abrir
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        // Se o app fechou antes, mostra SÓ o erro (nada mais é carregado)
        String last = Prefs.get(this).getString("ultimo_erro", null);
        if (last != null) {
            root.removeAllViews();
            title("O app fechou na última vez");
            text("Versão " + BuildConfig.VERSION_NAME + ". Tire um print desta tela e envie:");
            button("Apagar erro e continuar", v -> { Prefs.get(this).edit().remove("ultimo_erro").commit(); build(); });
            text(last);
            return;
        }
        try {
            buildUi();
        } catch (Throwable e) {
            root.removeAllViews();
            title("Erro");
            text("Tire um print desta tela e envie:\n\n" + android.util.Log.getStackTraceString(e));
        }
    }

    private void buildUi() {
        root.removeAllViews();
        SharedPreferences p = Prefs.get(this);

        title("Bloqueio por SIM  v" + BuildConfig.VERSION_NAME);
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
                exceptionsBox(h, n - 1);
            }
        }

        // Atualização
        header("Atualização");
        text("Versão instalada: " + BuildConfig.VERSION_NAME);
        button("Verificar atualização", v -> Updater.check(this, false));

        // 4. Histórico
        header("Últimas chamadas bloqueadas");
        String log = p.getString(Prefs.KEY_LOG, "");
        text(log.isEmpty() ? "Nenhuma ainda." : log);
        if (!log.isEmpty()) button("Limpar histórico", v -> {
            p.edit().remove(Prefs.KEY_LOG).apply();
            build();
        });
    }

    /** Área de exceções (DDDs e números liberados) de um chip. */
    private void exceptionsBox(PhoneAccountHandle h, int simNumber) {
        String k = Prefs.key(h);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(32), 0, 0, dp(12));

        TextView t = new TextView(this);
        t.setText("Exceções do SIM " + simNumber + " (sempre podem ligar):");
        t.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(t);

        TextView l1 = new TextView(this);
        l1.setText("DDDs liberados (separe por vírgula). Ex: 31, 41");
        box.addView(l1);
        EditText ddd = new EditText(this);
        ddd.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        ddd.setHint("31, 41");
        ddd.setText(Prefs.get(this).getString(Prefs.KEY_DDD + k, ""));
        box.addView(ddd);

        TextView l2 = new TextView(this);
        l2.setText("Números liberados (um por linha, com DDD)");
        box.addView(l2);
        EditText nums = new EditText(this);
        nums.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        nums.setSingleLine(false);
        nums.setMinLines(2);
        nums.setHint("31 99999-8888");
        nums.setText(Prefs.get(this).getString(Prefs.KEY_NUMS + k, ""));
        box.addView(nums);

        Button save = new Button(this);
        save.setText("Salvar exceções do SIM " + simNumber);
        save.setOnClickListener(v -> {
            Prefs.get(this).edit()
                    .putString(Prefs.KEY_DDD + k, ddd.getText().toString().trim())
                    .putString(Prefs.KEY_NUMS + k, nums.getText().toString().trim())
                    .apply();
            Toast.makeText(this, "Exceções salvas", Toast.LENGTH_SHORT).show();
        });
        box.addView(save);
        root.addView(box);
    }

    // ---------- helpers ----------

    private boolean hasPerms() {
        return checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasRole() {
        try {
            RoleManager rm = getSystemService(RoleManager.class);
            return rm != null && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
        } catch (Throwable e) { return false; }
    }

    private void requestRole() {
        RoleManager rm = getSystemService(RoleManager.class);
        try {
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQ_ROLE);
            } else {
                android.widget.Toast.makeText(this, "Este aparelho não oferece filtro de chamadas", android.widget.Toast.LENGTH_LONG).show();
            }
        } catch (Throwable e) {
            android.widget.Toast.makeText(this, "Erro: " + e, android.widget.Toast.LENGTH_LONG).show();
        }
    }

    @SuppressWarnings("MissingPermission")
    private List<PhoneAccountHandle> listSims() {
        TelecomManager tm = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        try {
            return tm.getCallCapablePhoneAccounts();
        } catch (Throwable e) {
            return java.util.Collections.emptyList();
        }
    }

    private String label(PhoneAccountHandle h) {
        try {
            TelecomManager tm = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
            PhoneAccount a = tm.getPhoneAccount(h);
            if (a != null && a.getLabel() != null) return a.getLabel().toString();
        } catch (Throwable ignored) {}
        return "Chip";
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) { root.post(this::build); }

    @Override
    protected void onActivityResult(int req, int res, android.content.Intent d) { root.post(this::build); }

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
