package com.kavacon.walletvulnerable;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * WalletVulnerable — demo educativa para Kavacon.
 *
 * App ficticia, SIN conexión a red (no declara permiso INTERNET), que simula una billetera
 * con tarjetas de crédito de prueba (números estándar de la industria para testing, no son
 * tarjetas reales). Contiene vulnerabilidades deliberadas:
 *   1. Almacenamiento en texto plano (ver {@link CardStorage}).
 *   2. PAN y CVV completos visibles en la UI, con un contentDescription predecible en
 *      cada campo (card_number, card_holder, card_expiry, card_cvv), pensados para ser
 *      leídos por un AccessibilityService de demostración (ver proyecto A11yReaderDemo).
 *   3. Logging de datos sensibles en claro.
 *
 * La creación de tarjetas vive en una pantalla separada ({@link AddCardActivity}), con
 * validación y auto-formato. Todas las tarjetas (las de arranque y las agregadas en
 * vivo) se pintan aquí con el mismo método {@link #buildCardView(Card)}, así que no hay
 * ninguna diferencia de lectura entre una tarjeta "vieja" y una nueva.
 *
 * Contraparte remediada: proyecto WalletSecure.
 */
public class MainActivity extends AppCompatActivity {

    private CardStorage storage;
    private LinearLayout cardsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cardsContainer = findViewById(R.id.cards_container);
        storage = new CardStorage(this);

        // Datos de prueba estándar de la industria (no son tarjetas reales).
        Card[] seed = new Card[]{
                new Card("VISA", "4111 1111 1111 1111", "JUAN PEREZ", "12/29", "123"),
                new Card("MASTERCARD", "5500 0000 0000 0004", "MARIA LOPEZ", "08/28", "456"),
                new Card("AMEX", "3400 0000 0000 009", "CARLOS RUIZ", "03/27", "7890"),
        };
        storage.seedIfEmpty(seed);

        Button btnOpenAddCard = findViewById(R.id.btn_open_add_card);
        btnOpenAddCard.setOnClickListener(v ->
                startActivity(new Intent(this, AddCardActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recarga siempre al volver (incluye tarjetas agregadas en AddCardActivity).
        refreshCards();
    }

    private void refreshCards() {
        cardsContainer.removeAllViews();
        List<Card> cards = storage.loadAllCards();
        for (Card card : cards) {
            cardsContainer.addView(buildCardView(card));
        }
    }

    /** Construye el bloque visual de una tarjeta. Mismo método para todas, sin distinción. */
    private LinearLayout buildCardView(Card card) {
        float d = getResources().getDisplayMetrics().density;
        int dp16 = (int) (16 * d);
        int dp12 = (int) (12 * d);
        int dp8 = (int) (8 * d);

        LinearLayout cardView = new LinearLayout(this);
        cardView.setOrientation(LinearLayout.VERTICAL);
        cardView.setPadding(dp16, dp16, dp16, dp16);
        cardView.setBackgroundColor(brandColor(card.brand));
        LinearLayout.LayoutParams rootLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rootLp.bottomMargin = dp12;
        cardView.setLayoutParams(rootLp);

        TextView brandView = new TextView(this);
        brandView.setText(card.brand);
        brandView.setTextColor(Color.WHITE);
        brandView.setTypeface(Typeface.DEFAULT_BOLD);
        brandView.setTextSize(14);
        cardView.addView(brandView);

        TextView numberView = new TextView(this);
        numberView.setText(card.number); // VULNERABLE: PAN completo
        numberView.setTextColor(Color.WHITE);
        numberView.setTextSize(20);
        numberView.setLetterSpacing(0.08f);
        numberView.setContentDescription("card_number");
        LinearLayout.LayoutParams numberLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        numberLp.topMargin = dp12;
        numberView.setLayoutParams(numberLp);
        cardView.addView(numberView);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp12;
        row.setLayoutParams(rowLp);

        row.addView(buildLabeledField("Titular", card.holder, "card_holder", 1f, 0));
        row.addView(buildLabeledField("Vence", card.expiry, "card_expiry", 0f, dp12));
        row.addView(buildLabeledField("CVV", card.cvv, "card_cvv", 0f, dp8)); // VULNERABLE: CVV completo
        cardView.addView(row);

        return cardView;
    }

    private LinearLayout buildLabeledField(String label, String value, String contentDescriptionTag,
                                            float weight, int marginStartPx) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = weight > 0
                ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = marginStartPx;
        column.setLayoutParams(lp);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(Color.parseColor("#C9D1D9"));
        labelView.setTextSize(10);
        column.addView(labelView);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(Color.WHITE);
        valueView.setTextSize(13);
        valueView.setContentDescription(contentDescriptionTag);
        column.addView(valueView);

        return column;
    }

    private int brandColor(String brand) {
        if (brand == null) return Color.parseColor("#30363D");
        switch (brand.toUpperCase()) {
            case "VISA": return Color.parseColor("#1F6FEB");
            case "MASTERCARD": return Color.parseColor("#DA3633");
            case "AMEX": return Color.parseColor("#30363D");
            default: return Color.parseColor("#6E7681");
        }
    }
}
