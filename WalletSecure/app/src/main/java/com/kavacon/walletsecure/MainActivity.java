package com.kavacon.walletsecure;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * WalletSecure — contraparte REMEDIADA de WalletVulnerable para la demo de Kavacon.
 *
 * Misma lógica de negocio que WalletVulnerable (incluida la pantalla separada
 * {@link AddCardActivity} con auto-formato y validación), pero con las mitigaciones:
 *   - Storage cifrado (ver {@link CardStorage}); el CVV nunca se persiste.
 *   - El PAN se muestra enmascarado por defecto (solo últimos 4 dígitos). El botón
 *     "Mostrar números" permite revelar el PAN completo EN PANTALLA para el usuario
 *     legítimo — pero el contenedor que lo pinta tiene
 *     {@code importantForAccessibility = NO_HIDE_DESCENDANTS} de forma PERMANENTE,
 *     sin importar el estado del botón. Esa es la demostración clave: lo que decide
 *     si un AccessibilityService externo puede leer el campo no es si el dato está
 *     enmascarado o visible, sino si el nodo está excluido del árbol de accesibilidad.
 *     Revelar el número en pantalla no habilita su lectura por A11yReaderDemo.
 *   - CVV nunca se muestra en texto real ("•••" estático), ni siquiera al revelar.
 *   - FLAG_SECURE: bloquea capturas de pantalla / grabación de esta Activity.
 *   - Sin logging de datos sensibles.
 */
public class MainActivity extends AppCompatActivity {

    private CardStorage storage;
    private LinearLayout cardsContainer;
    private Button btnToggleReveal;
    private boolean revealed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_main);

        cardsContainer = findViewById(R.id.cards_container);
        btnToggleReveal = findViewById(R.id.btn_toggle_reveal);
        storage = new CardStorage(this);

        // Mismos datos de prueba que WalletVulnerable (no son tarjetas reales).
        Card[] seed = new Card[]{
                new Card("VISA", "4111 1111 1111 1111", "JUAN PEREZ", "12/29"),
                new Card("MASTERCARD", "5500 0000 0000 0004", "MARIA LOPEZ", "08/28"),
                new Card("AMEX", "3400 0000 0000 009", "CARLOS RUIZ", "03/27"),
        };
        storage.seedIfEmpty(seed);

        Button btnOpenAddCard = findViewById(R.id.btn_open_add_card);
        btnOpenAddCard.setOnClickListener(v ->
                startActivity(new Intent(this, AddCardActivity.class)));

        btnToggleReveal.setOnClickListener(v -> {
            revealed = !revealed;
            updateToggleLabel();
            refreshCards();
        });
        updateToggleLabel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recarga siempre al volver (incluye tarjetas agregadas en AddCardActivity).
        refreshCards();
    }

    private void updateToggleLabel() {
        btnToggleReveal.setText(revealed ? "Ocultar números" : "Mostrar números");
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

        // Contenedor del PAN: SIEMPRE excluido del árbol de accesibilidad, sin
        // importar si "revealed" está activo o no. El botón "Mostrar números"
        // cambia lo que ve el usuario en pantalla; no cambia lo que un
        // AccessibilityService externo puede leer.
        LinearLayout numberContainer = new LinearLayout(this);
        numberContainer.setOrientation(LinearLayout.VERTICAL);
        numberContainer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        LinearLayout.LayoutParams numberContainerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        numberContainerLp.topMargin = dp12;
        numberContainer.setLayoutParams(numberContainerLp);

        TextView numberView = new TextView(this);
        numberView.setText(revealed ? card.fullNumber : card.maskedNumber());
        numberView.setTextColor(Color.WHITE);
        numberView.setTextSize(20);
        numberView.setLetterSpacing(0.08f);
        numberView.setContentDescription("card_number");
        numberContainer.addView(numberView);
        cardView.addView(numberContainer);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp12;
        row.setLayoutParams(rowLp);

        row.addView(buildLabeledField("Titular", card.holder, "card_holder", 1f, 0, false));
        row.addView(buildLabeledField("Vence", card.expiry, "card_expiry", 0f, dp12, false));
        // CVV nunca se guarda ni se revela: siempre placeholder, y siempre excluido.
        row.addView(buildLabeledField("CVV", "•••", "card_cvv", 0f, dp8, true));
        cardView.addView(row);

        return cardView;
    }

    private LinearLayout buildLabeledField(String label, String value, String contentDescriptionTag,
                                            float weight, int marginStartPx, boolean excludeFromAccessibility) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        if (excludeFromAccessibility) {
            column.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
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
