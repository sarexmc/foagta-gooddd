# Fogata de Resurrección — Forge 1.20.1

Sin librerías ni resource pack: todo (modelos, texturas, sonidos, efectos) va dentro del .jar.

## Compilar
1. Copia el wrapper de Gradle del MDK oficial de Forge 1.20.1 (`gradlew`, `gradlew.bat`, `gradle/`) — o usa el workflow de GitHub incluido.
2. Java 17:  `./gradlew build`  ->  `build/libs/*.jar`  (va en `mods/` del servidor Y del cliente).

## Cómo se usa
- **Cuchara de la Resurrección**: sin ella la fogata no abre. Se gasta al revivir ("Has perdido tu alma por revivir a alguien").
  `/give @s revivalcampfire:resurrection_spoon` · receta: tótem / esmeralda / palo (vertical).
- **Fogata de Resurrección**: `/give @s revivalcampfire:resurrection_campfire` · receta: tótem arriba, arena de almas, fogata al centro.
- Con la cuchara en la mano: click derecho en la fogata -> menú con los baneados -> elegir -> **Revivir**.
- Comandos (OP; en singleplayer siempre):
  - `/revivir <jugador>` / `/revivir <jugador> <x y z>` ritual por comando
  - `/revivir demo` animación de prueba con un jugador ficticio
  - `/revivir testban <nombre>` banea un jugador ficticio para probar el menú

## Probar en singleplayer
`/revivir testban Pepe` -> `/give @s revivalcampfire:resurrection_spoon` + fogata -> click derecho con la cuchara -> Pepe -> Revivir.
O directamente `/revivir demo` para ver la animación.

## Configuración (`config/revivalcampfire-common.toml`)
- `banOnDeath` (false): banea al morir; déjalo en false si ya tienes tu propio sistema de baneo.
- `requiredPermissionLevel` (0): permiso extra además de la cuchara (2 = solo OPs).

## Arte y sonido
- **Fogata:** modelo propio del estudio (`models/custom/fogata.json`, ~5 bloques de ancho y casi 5 de alto) dibujado por un
  renderizador de entidad de bloque (`FogataModel`). Los troncos usan la corteza de abeto de Minecraft
  (`spruce_log` / `spruce_log_top`); el resto usa el atlas `textures/block/fogata.png`.
- **Efectos de luz:** texturas del estudio en `textures/effect/` (aros, brillo, centro, cinta, estrella, grieta, halo, haz, lobulos, rayos...).
- **Audio:** `sounds/ritual_main.ogg` (30,6 s) y `sounds/revived.ogg` (3,1 s) son la banda sonora del estudio; `spoon_use.ogg` lo sintetiza `tools/generate_sounds.py`.
- La cuchara y el menú los dibuja `tools/generate_art.py`.
- Los derechos de los archivos del estudio son responsabilidad de quien publique el mod.

## Línea de tiempo del ritual (sincronizada con ritual.ogg)
0 s golpe · 0,5-7 s tres círculos deformes con un punto brillante en el cielo · 4,5 s un cubo oscuro cae, impacta en la fogata (7,5 s),
agrieta el suelo y crece hasta envolverlo todo (9 s) · 9 s el mundo desaparece: vacío con estrellas, discos, arcos de luz orbitando ·
el que revive mira la luz de la fogata y luego la estrella dorada, que crece y SUBE hasta que la pantalla se vuelve blanca (28,5 s) ·
28,5 s destello, vuelve el día, se desbanea (revived.ogg) y el avatar del jugador (modelo y skin reales, sin armor stand) baja del cielo
con un haz de luz. El jugador reaparece donde aterriza su avatar la próxima vez que entre · 36,7 s fin.
