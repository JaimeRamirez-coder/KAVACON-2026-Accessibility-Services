package com.kavacon.walletvulnerable;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * VULNERABILIDAD INTENCIONAL (demo educativa):
 * Persiste los datos de tarjeta en SharedPreferences SIN cifrar. Cualquiera con acceso
 * a /data/data/com.kavacon.walletvulnerable/shared_prefs/ (adb backup, dispositivo rooteado,
 * o un backup inseguro) puede leer el PAN y el CVV en texto plano.
 *
 * Soporta una lista dinámica de tarjetas (no solo las 3 de arranque) para poder agregar
 * una tarjeta nueva en vivo durante la demo y mostrar cómo A11yReaderDemo la lee también.
 *
 * Esto es deliberado para la charla — contrastar con WalletSecure (EncryptedSharedPreferences).
 */
public class CardStorage {

    private static final String TAG = "WalletVulnerable";
    private static final String PREFS_NAME = "wallet_prefs_plain";
    private static final String KEY_COUNT = "card_count";

    private final SharedPreferences prefs;

    public CardStorage(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public int getCardCount() {
        return prefs.getInt(KEY_COUNT, 0);
    }

    private void saveCardAt(int index, Card card) {
        prefs.edit()
                .putString("card_" + index + "_brand", card.brand)
                .putString("card_" + index + "_number", card.number)
                .putString("card_" + index + "_holder", card.holder)
                .putString("card_" + index + "_expiry", card.expiry)
                .putString("card_" + index + "_cvv", card.cvv)
                .apply();
    }

    public Card loadCard(int index) {
        String number = prefs.getString("card_" + index + "_number", null);
        if (number == null) return null;
        return new Card(
                prefs.getString("card_" + index + "_brand", ""),
                number,
                prefs.getString("card_" + index + "_holder", ""),
                prefs.getString("card_" + index + "_expiry", ""),
                prefs.getString("card_" + index + "_cvv", "")
        );
    }

    public List<Card> loadAllCards() {
        List<Card> list = new ArrayList<>();
        int count = getCardCount();
        for (int i = 1; i <= count; i++) {
            Card card = loadCard(i);
            if (card != null) list.add(card);
        }
        return list;
    }

    /** Siembra las tarjetas de arranque solo si todavía no hay ninguna guardada. */
    public void seedIfEmpty(Card[] seed) {
        if (getCardCount() > 0) return;
        for (Card card : seed) {
            addCard(card);
        }
    }

    /** Agrega una tarjeta nueva al final (botón "Guardar tarjeta" del formulario en vivo). */
    public Card addCard(Card card) {
        int next = getCardCount() + 1;
        saveCardAt(next, card);
        prefs.edit().putInt(KEY_COUNT, next).apply();

        // VULNERABLE: se loguea el PAN y el CVV completos en claro.
        Log.d(TAG, "Tarjeta " + next + " guardada -> number=" + card.number
                + " holder=" + card.holder + " cvv=" + card.cvv);
        return card;
    }
}
