package com.kavacon.a11yreaderdemo;

import java.util.ArrayList;
import java.util.List;

/**
 * Puente EN MEMORIA entre {@link CardReaderService} y {@link MainActivity},
 * ambos dentro del mismo proceso de esta misma app.
 *
 * Reemplaza al overlay: en vez de dibujar una ventana encima de otras apps,
 * el servicio publica aquí lo que leyó y la Activity (si está visible) lo
 * pinta en su propia pantalla. El buffer vive solo en RAM, acotado a
 * {@link #MAX_ENTRIES} líneas, y se pierde al cerrar/matar el proceso — no
 * hay escritura a disco, base de datos ni red en ningún punto.
 */
public final class ReaderBridge {

    public interface Listener {
        void onUpdate(List<String> entries);
    }

    private static final int MAX_ENTRIES = 40;
    private static final List<String> entries = new ArrayList<>();
    private static volatile Listener listener;

    private ReaderBridge() {}

    /** La Activity se registra en onResume() y se quita en onPause(). */
    public static synchronized void setListener(Listener l) {
        listener = l;
        if (l != null) {
            l.onUpdate(new ArrayList<>(entries));
        }
    }

    public static synchronized void publish(String entry) {
        entries.add(entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
        Listener l = listener;
        if (l != null) {
            l.onUpdate(new ArrayList<>(entries));
        }
    }

    /** Vacía el buffer en memoria (botón "Borrar logs" de la UI). */
    public static synchronized void clear() {
        entries.clear();
        Listener l = listener;
        if (l != null) {
            l.onUpdate(new ArrayList<>(entries));
        }
    }
}
