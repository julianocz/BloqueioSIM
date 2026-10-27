package com.juliano.bloqueiosim;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.TextUtils;

/**
 * O Android chama este serviço a cada ligação recebida.
 * Bloqueia a chamada se ela chegou num chip marcado e o número não está nos contatos.
 */
public class BlockerService extends CallScreeningService {

    @Override
    public void onScreenCall(Call.Details details) {
        boolean block = false;
        String number = "";
        String simLabel = "?";
        try {
            if (details.getCallDirection() == Call.Details.DIRECTION_INCOMING) {
                SharedPreferences p = Prefs.get(this);
                PhoneAccountHandle acc = details.getAccountHandle();
                if (p.getBoolean(Prefs.KEY_ENABLED, false) && acc != null
                        && Prefs.blockedSims(this).contains(Prefs.key(acc))) {

                    simLabel = label(acc);
                    Uri handle = details.getHandle();
                    number = handle != null ? handle.getSchemeSpecificPart() : "";

                    if (TextUtils.isEmpty(number)) {
                        // Número oculto / privado
                        block = p.getBoolean(Prefs.KEY_BLOCK_HIDDEN, true);
                        number = "Número oculto";
                    } else {
                        block = !Prefs.isException(this, acc, number) && !isContact(number);
                    }
                }
            }
        } catch (Exception ignored) {
            block = false; // em caso de erro, nunca bloqueia
        }

        CallResponse.Builder r = new CallResponse.Builder();
        if (block) {
            r.setDisallowCall(true)
             .setRejectCall(true)
             .setSkipCallLog(false)      // continua aparecendo no histórico do telefone
             .setSkipNotification(true); // sem notificação de chamada perdida
            Prefs.addLog(this, number, simLabel);
        }
        respondToCall(details, r.build());
    }

    private boolean isContact(String number) {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return true; // sem permissão: por segurança, não bloqueia
        }
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        try (Cursor c = getContentResolver().query(uri,
                new String[]{ContactsContract.PhoneLookup._ID}, null, null, null)) {
            return c != null && c.getCount() > 0;
        }
    }

    private String label(PhoneAccountHandle h) {
        try {
            TelecomManager tm = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
            PhoneAccount a = tm.getPhoneAccount(h);
            if (a != null && a.getLabel() != null) return a.getLabel().toString();
        } catch (Exception ignored) {}
        return "SIM";
    }
}
