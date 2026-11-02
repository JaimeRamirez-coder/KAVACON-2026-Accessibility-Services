package com.kavacon.a11yreaderdemo;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * Único punto de activación (abre Ajustes > Accesibilidad) y, a la vez,
 * pantalla donde se ve la lectura en vivo que publica {@link CardReaderService}
 * a través de {@link ReaderBridge}. No hay overlay ni segundo permiso: todo
 * ocurre dentro de la propia interfaz de esta app.
 */
public class MainActivity extends AppCompatActivity implements ReaderBridge.Listener {

    private TextView tvLog;
    private ScrollView scrollLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnA11y = findViewById(R.id.btn_open_accessibility_settings);
        Button btnClear = findViewById(R.id.btn_clear_log);
        tvLog = findViewById(R.id.tv_log);
        scrollLog = findViewById(R.id.scroll_log);

        btnA11y.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        btnClear.setOnClickListener(v -> {
            ReaderBridge.clear();
            // Sin esto, si la pantalla objetivo no cambia tras borrar el log, el
            // servicio no vuelve a publicar nada hasta el próximo cambio real.
            CardReaderService.requestRecheck();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        ReaderBridge.setListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ReaderBridge.setListener(null);
    }

    @Override
    public void onUpdate(List<String> entries) {
        runOnUiThread(() -> {
            if (entries.isEmpty()) {
                tvLog.setText("Sin lecturas aún — activa el servicio y abre una de las wallets.");
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (String entry : entries) {
                sb.append(entry).append("\n\n");
            }
            tvLog.setText(sb.toString());
            scrollLog.post(() -> scrollLog.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }
}
