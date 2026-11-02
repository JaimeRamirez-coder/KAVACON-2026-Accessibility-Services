package com.kavacon.walletvulnerable;

import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.regex.Pattern;

/**
 * Pantalla dedicada para agregar una tarjeta (separada de MainActivity).
 *
 * Mientras el usuario escribe:
 *   - Número: solo dígitos, agrupados de 4 en 4 ("4111 1111 1111 1111").
 *   - Vencimiento: solo dígitos, con "/" automático después de los primeros 2 ("MM/AA").
 *   - Titular / marca: se capitalizan en mayúsculas (igual que una tarjeta física).
 *
 * Al guardar se valida el formato final con expresiones regulares; si algo no
 * calza, se marca el campo con setError() y no se guarda nada.
 *
 * La tarjeta se persiste igual que el resto (ver {@link CardStorage}): en texto
 * plano, vulnerabilidad intencional de la demo.
 */
public class AddCardActivity extends AppCompatActivity {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d{13,19}");
    private static final Pattern EXPIRY_PATTERN = Pattern.compile("(0[1-9]|1[0-2])/\\d{2}");
    private static final Pattern CVV_PATTERN = Pattern.compile("\\d{3,4}");
    private static final Pattern HOLDER_PATTERN = Pattern.compile("[A-Za-zÀ-ÿ ]{2,}");

    /** Rechaza cualquier inserción que contenga un carácter no numérico. */
    private static final InputFilter DIGITS_ONLY = (source, start, end, dest, dstart, dend) -> {
        for (int i = start; i < end; i++) {
            if (!Character.isDigit(source.charAt(i))) return "";
        }
        return null; // null = aceptar la inserción tal cual
    };

    private EditText etBrand;
    private EditText etNumber;
    private EditText etHolder;
    private EditText etExpiry;
    private EditText etCvv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_card);

        etBrand = findViewById(R.id.et_brand);
        etNumber = findViewById(R.id.et_number);
        etHolder = findViewById(R.id.et_holder);
        etExpiry = findViewById(R.id.et_expiry);
        etCvv = findViewById(R.id.et_cvv);
        Button btnSave = findViewById(R.id.btn_save_card);

        // Refuerzo en código: el atributo XML importantForAutofill no siempre basta para
        // evitar el bottom-sheet de "Autofill payment info" de Google Play Services, que
        // se dispara al enfocar un EditText numérico con pinta de campo de tarjeta y se
        // come la entrada de teclado. Lo desactivamos también en la ventana completa.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getWindow().getDecorView()
                    .setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
            for (EditText field : new EditText[]{etBrand, etNumber, etHolder, etExpiry, etCvv}) {
                field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            }
        }

        // Con android:inputType="number" el teclado numérico de Google Play Services
        // detecta el campo como de tarjeta y muestra su propio bottom-sheet de
        // "Autofill payment info" que se come la entrada de teclado — y además su
        // KeyListener asociado rechaza cualquier carácter no numérico insertado en el
        // Editable, incluido el espacio y el "/" que el formateador inserta por código.
        // Por eso número/vencimiento usan inputType="phone" (teclado numérico sin ese
        // comportamiento) y dejamos solo un límite de longitud como filtro; el propio
        // TextWatcher ya limpia cualquier carácter no numérico que el usuario escriba.
        etNumber.setFilters(new InputFilter[]{new InputFilter.LengthFilter(23)});
        etExpiry.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
        // CVV: inputType="textPassword" (no numérico) + filtro propio que solo deja dígitos.
        etCvv.setFilters(new InputFilter[]{DIGITS_ONLY, new InputFilter.LengthFilter(4)});

        etNumber.addTextChangedListener(groupingFormatter(etNumber, 4, 19));
        etExpiry.addTextChangedListener(expiryFormatter(etExpiry));

        btnSave.setOnClickListener(v -> onSaveClicked());
    }

    private void onSaveClicked() {
        String brand = etBrand.getText().toString().trim().toUpperCase();
        String number = etNumber.getText().toString().replaceAll("\\s", "");
        String holder = etHolder.getText().toString().trim().toUpperCase();
        String expiry = etExpiry.getText().toString().trim();
        String cvv = etCvv.getText().toString().trim();

        if (brand.isEmpty()) brand = "TARJETA";

        boolean valid = true;
        if (!NUMBER_PATTERN.matcher(number).matches()) {
            etNumber.setError("Número inválido (13 a 19 dígitos)");
            valid = false;
        }
        if (!HOLDER_PATTERN.matcher(holder).matches()) {
            etHolder.setError("Ingresa el nombre del titular");
            valid = false;
        }
        if (!EXPIRY_PATTERN.matcher(expiry).matches()) {
            etExpiry.setError("Formato esperado: MM/AA");
            valid = false;
        }
        if (!CVV_PATTERN.matcher(cvv).matches()) {
            etCvv.setError("CVV inválido (3 o 4 dígitos)");
            valid = false;
        }
        if (!valid) return;

        // Se guarda con el número agrupado (mismo formato que las tarjetas de ejemplo).
        Card card = new Card(brand, groupDigits(number, 4), holder, expiry, cvv);

        CardStorage storage = new CardStorage(this);
        storage.addCard(card);

        Toast.makeText(this, "Tarjeta agregada", Toast.LENGTH_SHORT).show();
        finish();
    }

    private static String groupDigits(String digits, int groupSize) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && i % groupSize == 0) sb.append(' ');
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }

    /** TextWatcher genérico: deja solo dígitos y los agrupa cada {@code groupSize}. */
    private static TextWatcher groupingFormatter(EditText target, int groupSize, int maxDigits) {
        return new TextWatcher() {
            private boolean selfEditing = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (selfEditing) return;
                selfEditing = true;

                String digits = s.toString().replaceAll("\\D", "");
                if (digits.length() > maxDigits) digits = digits.substring(0, maxDigits);
                String formatted = groupDigits(digits, groupSize);

                s.replace(0, s.length(), formatted);
                target.setError(null);
                selfEditing = false;
            }
        };
    }

    /** TextWatcher: deja solo dígitos (máx. 4) e inserta "/" después del mes -> "MM/AA". */
    private static TextWatcher expiryFormatter(EditText target) {
        return new TextWatcher() {
            private boolean selfEditing = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (selfEditing) return;
                selfEditing = true;

                String digits = s.toString().replaceAll("\\D", "");
                if (digits.length() > 4) digits = digits.substring(0, 4);

                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < digits.length(); i++) {
                    if (i == 2) formatted.append('/');
                    formatted.append(digits.charAt(i));
                }

                s.replace(0, s.length(), formatted.toString());
                target.setError(null);
                selfEditing = false;
            }
        };
    }
}
