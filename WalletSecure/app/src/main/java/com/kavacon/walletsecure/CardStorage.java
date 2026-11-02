package com.kavacon.walletsecure;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

/**
 * MITIGACIÓN (demo educativa): a diferencia de WalletVulnerable, el PAN y los demás
 * datos se cifran en reposo con EncryptedSharedPreferences (Jetpack Security), que
 * usa un MasterKey resguardado en el Android Keystore. El CVV deliberadamente NO se
 * persiste en ningún lado (ni cifrado): se valida en el formulario y se descarta.
 *
 * Soporta una lista dinámica de tarjetas (no solo las 3 de arranque), igual que
 * WalletVulnerable, para poder agregar una tarjeta nueva en vivo durante la demo.
 */
public class CardStorage {

    private static final String PREFS_NAME = "wallet_prefs_encrypted";
    private static final String KEY_COUNT = "card_count";

    private final SharedPreferences prefs;

    public CardStorage(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            prefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("No se pudo inicializar el storage cifrado", e);
        }
    }

    public int getCardCount() {
        return prefs.getInt(KEY_COUNT, 0);
    }

    private void saveCardAt(int index, Card card) {
        prefs.edit()
                .putString("card_" + index + "_brand", card.brand)
                .putString("card_" + index + "_number", card.fullNumber)
                .putString("card_" + index + "_holder", card.holder)
                .putString("card_" + index + "_expiry", card.expiry)
                .apply();
        // Nota: el CVV nunca se escribe a storage, ni cifrado.
    }

    public Card loadCard(int index) {
        String number = prefs.getString("card_" + index + "_number", null);
        if (number == null) return null;
        return new Card(
                prefs.getString("card_" + index + "_brand", ""),
                number,
                prefs.getString("card_" + index + "_holder", ""),
                prefs.getString("card_" + index + "_expiry", "")
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
        return card;
    }
}
