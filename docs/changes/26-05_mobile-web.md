# Cambios en mobile-web/ - 26/05/2026

## Resumen
Fix de la función `flashScreen()` que evita bugs con temas concurrentes y múltiples flashes.

---

## Arreglos Realizados

### 1. Fix de flashScreen (restauración correcta del tema)
**Problema**: La función `flashScreen()` usaba `getComputedStyle()` para obtener el color de fondo actual y lo guardaba como inline style. Esto causaba dos bugs:
1. **Tema roto**: Al restaurar el color original (ej: `rgb(26,26,46)`), el inline style anulaba las clases CSS del tema. Si el usuario cambiaba de claro/oscuro después de un flash, el fondo no actualizaba.
2. **Flashes concurrentes**: Si llegaban 2 mensajes rápidos, el segundo sobrescribía el "original" del primero, y cuando el primer timeout acababa restauraba un color incorrecto.

**Solución**:
- Guardar solo el valor inline previo (`document.body.style.backgroundColor`), no el computed style.
- Si no había inline style previo, al restaurar se limpia con `''` para que vuelvan a aplicarse las clases CSS del tema.
- Añadido `_flashTimeout` para cancelar el timeout anterior si llega un nuevo flash antes de que acabe el anterior.

**Antes**:
```javascript
flashScreen(color, duration) {
    const computedStyle = getComputedStyle(document.body);
    const original = document.body.style.backgroundColor || computedStyle.backgroundColor;
    document.body.style.backgroundColor = color;
    setTimeout(() => {
        document.body.style.backgroundColor = original;
    }, duration);
}
```

**Después**:
```javascript
flashScreen(color, duration) {
    if (this._flashTimeout) {
        clearTimeout(this._flashTimeout);
    }
    const original = document.body.style.backgroundColor;
    document.body.style.backgroundColor = color;
    this._flashTimeout = setTimeout(() => {
        if (original) {
            document.body.style.backgroundColor = original;
        } else {
            document.body.style.backgroundColor = '';
        }
        this._flashTimeout = null;
    }, duration);
}
```

**Archivos modificados**:
- `mobile-web/script.js` - Función `flashScreen()` en `controllerScreen`

---

### 2. Fix de vibración para gama baja (1001ms)
**Problema**: En dispositivos de gama baja (Android económicos), las vibraciones cortas (<200ms) y patrones complejos con pausas breves son ignorados por el sistema operativo o el motor de vibración no responde a pulsos tan cortos.

**Solución**: Todas las vibraciones ahora usan **1001ms** (o `[0, 1001]` con pausa inicial), que es el umbral mínimo que los dispositivos de gama baja suelen respetar:
- `collision`: `100` → `1001`
- `death`: `[0, 500, 200, 500]` → `[0, 1001]`
- `pickup`: `50` → `1001`
- `screen_effect`: `Math.max(data.duration, 100)` → `1001`
- `puzzle_start`: `100` → `1001`
- `custom`: `100` → `1001`
- `testVibration()`: Patrón complejo de 12 pulsos → `1001` simple

**Archivos modificados**:
- `mobile-web/script.js` - Todas las llamadas `this.vibrate()` en `handleServerMessage()` y `testVibration()`

### 3. Contador de pickups (monedas) flotante
**Implementación**:
- Badge flotante en la esquina superior derecha de `controller-main` con icono de moneda y contador numérico
- Se incrementa automáticamente al recibir mensajes `pickup` del servidor
- Muestra el tipo de objeto recogido en el log (`data.pickupType`)
- Se reinicia a 0 automáticamente al recargar la página (sin persistencia)
- Estilo: fondo oscuro, borde dorado `#FFD700`, sombra suave, posición absoluta flotante
- Icono Material Symbols: `monetization_on`

**Archivos modificados**:
- `mobile-web/index.html` - Añadido `.pickup-counter` dentro de `controller-main`
- `mobile-web/style.css` - Estilos del contador flotante + `position: relative` en `.controller-main`
- `mobile-web/script.js` - Estado `pickupCount`, cache del elemento, incremento en `handleServerMessage('pickup')`

### 4. Sincronización con java-server
**Implementación**:
- Los cambios realizados en `mobile-web/` (index.html, style.css, script.js) han sido consolidados en el archivo standalone `java-server/src/main/resources/index.html`.
- Esta consolidación asegura que el servidor Java sirva la versión más reciente del mando web sin dependencias externas.

---

## Archivos Afectados

### Modificados
- `mobile-web/index.html`
- `mobile-web/style.css`
- `mobile-web/script.js`
- `java-server/src/main/resources/index.html` (consolidación)
- `java-server/run_server.bat` (nuevo archivo)
- `.gitignore` (actualización de reglas)

---

## Estado Actual
La función `flashScreen()` ahora funciona correctamente con cambios de tema (claro/oscuro) y múltiples flashes consecutivos sin bugs de restauración. Se añadió contador de pickups flotante visible durante el juego.
