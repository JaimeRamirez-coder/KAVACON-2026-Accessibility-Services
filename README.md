# Accessibility Services: el permiso de inclusión que se convirtió en arma

Demo educativa para **Kavacon 2026 (Paraguay)** — charla de Jaime Ramírez sobre cómo el
malware bancario moderno abusa de los **Accessibility Services** de Android para robar
datos de tarjetas sin tocar la pantalla.

Tres apps Android, **100% ficticias y aisladas**, que ilustran el ataque y su mitigación
de forma controlada: sin root, sin exfiltración real, sin datos de ninguna persona u
organización real.

> ⚠️ **Uso previsto**: material de capacitación en seguridad / concientización. No está
> pensado ni probado para apuntar a apps de terceros. Ver [Alcance y seguridad](#alcance-y-seguridad-del-poc).

## Resumen

| App | Rol en la demo | Qué muestra |
|---|---|---|
| **[WalletVulnerable](WalletVulnerable/)** | "Banco" vulnerable | Billetera con tarjetas de prueba (números estándar de la industria). PAN y CVV en texto plano, storage sin cifrar, sin protecciones de accesibilidad. |
| **[WalletSecure](WalletSecure/)** | "Banco" remediado | Misma app y flujo, pero con las mitigaciones aplicadas: storage cifrado, campos sensibles excluidos del árbol de accesibilidad, `FLAG_SECURE`. |
| **[A11yReaderDemo](A11yReaderDemo/)** | "Atacante" | `AccessibilityService` restringido únicamente a las dos apps de arriba. Lee los campos vía `AccessibilityNodeInfo` y muestra la lectura en vivo en su propia pantalla. |

El punto central de la demo: el mismo lector funciona perfecto contra `WalletVulnerable`
y **falla** contra `WalletSecure` — incluso cuando `WalletSecure` muestra el número de
tarjeta completo en pantalla con el botón "Mostrar números". Lo que protege el dato no
es ocultarlo visualmente, es excluirlo del árbol de accesibilidad
(`importantForAccessibility = NO_HIDE_DESCENDANTS`).

## Alcance y seguridad del PoC

Estas apps fueron diseñadas deliberadamente para ser **inertes fuera del trío**:

- **`A11yReaderDemo` no declara el permiso `INTERNET`** — estructuralmente no puede
  exfiltrar nada a ningún servidor, aunque quisiera.
- Su `AccessibilityService` está restringido por configuración
  (`res/xml/accessibility_service_config.xml`, atributo `android:packageNames`) **y**
  por un chequeo redundante en código a los paquetes `com.kavacon.walletvulnerable` y
  `com.kavacon.walletsecure` exclusivamente. No recibe eventos de ninguna otra app
  instalada en el dispositivo.
- Todo lo leído se publica en un buffer en memoria (`ReaderBridge`), acotado y visible
  solo en la propia pantalla de `A11yReaderDemo`. No hay persistencia a disco, base de
  datos, ni red.
- Los datos de tarjeta son **números de prueba estándar de la industria** (p. ej.
  `4111 1111 1111 1111`), no corresponden a tarjetas ni personas reales.
- Ninguna app requiere root, Xposed/LSPosed, ni ningún permiso fuera de lo que el
  usuario concede explícitamente desde Ajustes del sistema (Accesibilidad).

No se incluye ningún mecanismo de phishing, sideload automatizado, exfiltración, C2,
ni nada que opere fuera de este conjunto de 3 apps.

## Cómo funciona cada app

### WalletVulnerable (vulnerable, a propósito)

- Lista de tarjetas con PAN, titular, vencimiento y **CVV completos, visibles**.
- Pantalla separada para agregar tarjetas (`AddCardActivity`) con auto-formato en vivo
  (número agrupado de 4 en 4, vencimiento `MM/AA` con `/` automático) y validación.
- Storage en `SharedPreferences` **sin cifrar** + logging del PAN/CVV en claro
  (`CardStorage.java`) — vulnerabilidad intencional.
- Cada campo sensible (`tv_card_number_N`, o el `contentDescription` equivalente para
  tarjetas agregadas en vivo) queda expuesto normalmente en el árbol de accesibilidad.

### WalletSecure (contraparte remediada)

- Mismo flujo y misma UI que `WalletVulnerable`, mismos datos de prueba.
- `EncryptedSharedPreferences` (Jetpack Security / Android Keystore) para el storage.
- El CVV se valida en el formulario pero **nunca se persiste**, ni cifrado.
- PAN enmascarado por defecto (`•••• •••• •••• 1111`); botón **"Mostrar números"** para
  revelar el PAN completo en pantalla — pero el contenedor que lo pinta tiene
  `importantForAccessibility = NO_HIDE_DESCENDANTS` **de forma permanente**, sin
  importar el estado del botón.
- `FLAG_SECURE` en la ventana (bloquea capturas de pantalla / grabación).

### A11yReaderDemo (lector / "atacante" simulado)

- `AccessibilityService` que sondea la ventana activa cada ~700 ms (no depende
  únicamente de `AccessibilityEvent`, que Android puede limitar si la pantalla no
  cambia) y recorre el árbol de nodos buscando campos cuyo `resource-id` o
  `contentDescription` contenga `card_number`, `card_holder`, `card_expiry` o
  `card_cvv`.
- Publica lo encontrado en su propia UI (`MainActivity`) en vivo, vía un buffer en
  memoria acotado — sin overlay, sin segundo permiso, sin red.
- Botón "Borrar logs" que limpia el buffer y fuerza una relectura inmediata.

## Requisitos

- Android Studio (o Gradle 8.7 + JDK 17) y Android SDK con `compileSdk 34`.
- Un dispositivo o emulador con **Android 10 (API 29) o superior**.
- No se requiere root.

## Compilar e instalar

Cada carpeta es un proyecto Gradle independiente.

```bash
cd WalletVulnerable
cp local.properties.example local.properties   # y ajusta sdk.dir a tu SDK
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Repite lo mismo para `WalletSecure` y `A11yReaderDemo`. Los tres pueden convivir
instalados en el mismo dispositivo sin conflicto (paquetes distintos).

## Correr la demo

1. Instala las tres apps.
2. Abre **A11yReaderDemo** → botón **"Activar servicio de accesibilidad"** → actívalo
   en Ajustes del sistema (`Accesibilidad → Demo Kavacon: Lector de accesibilidad`).
3. Abre **WalletVulnerable** y vuelve a **A11yReaderDemo**: la lectura de PAN, CVV,
   titular y vencimiento aparece en la pantalla, en vivo.
4. Abre **WalletSecure**, toca **"Mostrar números"** para revelar el PAN completo, y
   vuelve a **A11yReaderDemo**: solo se leen `card_holder` y `card_expiry` — `card_number`
   y `card_cvv` no aparecen, pese a estar visibles en pantalla.
5. (Opcional) En cualquiera de las dos wallets, usa **"+ Agregar tarjeta"** para crear
   una tarjeta nueva en vivo frente al público y repetir el paso 3 o 4 con ella.

## Estructura del repositorio

```
.
├── WalletVulnerable/     # App "banco" vulnerable
├── WalletSecure/         # App "banco" remediada
├── A11yReaderDemo/       # App lectora (AccessibilityService)
└── LICENSE
```

## Licencia

MIT — ver [LICENSE](LICENSE).

## Autor

**Jaime Ramírez** — Cybersecurity Researcher
[LinkedIn](https://www.linkedin.com/in/jaime-patricio-ramirez/) · [GitHub](https://github.com/JaimeRamirez-coder)
