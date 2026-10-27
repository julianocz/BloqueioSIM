package com.juliano.bloqueiosim;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

/** Mostra o erro na tela para o usuário tirar print. */
public class ErrorActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView t = new TextView(this);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        t.setPadding(p, p * 2, p, p);
        t.setTextSize(13);
        t.setTextIsSelectable(true);
        t.setText("O app encontrou um erro (versão " + BuildConfig.VERSION_NAME + ").\n"
                + "Tire um print desta tela e envie:\n\n" + getIntent().getStringExtra("erro"));
        ScrollView sv = new ScrollView(this);
        sv.addView(t);
        setContentView(sv);
    }
}
