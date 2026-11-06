# Tareas pendientes — LiMusic móvil

Lista de trabajo viva. Se actualiza al empezar y terminar cada tarea; lo ya hecho se registra en
`CAMBIOS_MOVIL.md`. Cada fase termina con prueba en el teléfono y un commit propio.

Estado: `[ ]` pendiente · `[~]` en curso · `[x]` hecho · `[?]` necesita una decisión tuya

Orden recomendado: 1 → 2 → 3. La fase 1 es rápida y limpia la interfaz antes de rediseñarla.

---

## Fase 1 — Quitar lo que no tiene sentido en móvil

- [ ] **Discord fuera de Android:** quitar del menú **Más** las dos entradas («Presencia en Discord» y
  «Discord»), la sección Discord de Configuración y su vista previa. En el backend, no arrancar el cliente
  de Discord en Android: ahora intenta conectar cada 15 s sin éxito (se ve en los registros).
- [ ] **Menú Más más corto:** «Abrir reproductor», «Cola», «Letra» y «Comentarios» repiten lo que ya hay
  en el reproductor ampliado. Quitarlos del menú.
- [ ] **Buscar actualizaciones:** el actualizador es el de escritorio y no sabe nada de Android. Ocultarlo
  en móvil (o, más adelante, comprobar versiones de la APK).
- [ ] **Opciones de escritorio en Configuración** (bandeja del sistema, inicio automático, iniciar
  minimizado, barra de título del sistema…): hoy se ocultan por **ancho de pantalla**, así que en una
  tableta o en horizontal vuelven a aparecer. Ocultarlas por plataforma (`isAndroid`).
- [ ] **Minirreproductor de escritorio y atajos de teclado:** confirmar que no queda ningún acceso en
  Android (los atajos ya están ocultos).
- [ ] **Last.fm en el menú Más:** el texto «Last.fm» hace de icono y desalinea la fila; usar el icono
  como en las demás entradas.

## Fase 2 — Bugs y problemas visuales

Vistos en el Xiaomi (06/11/2026):

- [ ] **Inicio:** al desplazar, la barra de chips (Todo, Pódcasts…) se queda fija pero por debajo de la
  barra de estado, y el reloj y los iconos se solapan con ella. Falta el margen del área segura superior.
- [ ] **Inicio:** asoma un botón morado flotante por detrás de la pestaña «Más» de la barra inferior.
- [ ] **Reproductor ampliado:** no ocupa la pantalla. Siguen visibles la barra compacta (con sus propios
  controles) y la navegación inferior, la portada es pequeña y el nombre del artista se parte en dos
  líneas aunque sobra espacio. (Se rediseña en la fase 3; aquí, como mínimo, que tape la barra compacta.)
- [ ] **Buscar:** el texto de ejemplo del buscador se corta («…, artista»).

- [ ] **Barra de estado en todas las páginas:** al desplazar, el contenido pasa por debajo del reloj y los
  iconos sin ningún fondo (visto en Inicio y en la página de artista). Añadir un fondo o degradado bajo la
  barra de estado.
- [ ] **Configuración:** el título de la sección sale dos veces (en el selector y justo debajo). Además, por
  encima del panel se ve la página de detrás bajo la barra de estado.
- [ ] **Página de artista:** en «Canciones principales», los botones de me gusta, no me gusta y menú
  ocupan tanto ancho que el título se corta («Que Fue Lo Q…») y «47 M reproducciones» se parte en dos
  líneas, lo que hace las filas muy altas. En móvil, pasar me gusta/no me gusta al menú de tres puntos.
- [ ] **Páginas de detalle sin botón de volver** (artista, álbum…): solo se puede volver con el gesto o
  botón atrás de Android. Añadir una flecha visible arriba a la izquierda.
- [ ] **PoToken no disponible en Android** (aviso en los registros: «BotGuard is not available in this
  Android build»). Hoy todo suena por VISIONOS; si YouTube empieza a exigir PoToken, la reproducción
  podría fallar con errores 403. Vigilar.

Pendientes heredados de `CAMBIOS_MOVIL.md`:

- [ ] Probar **Escuchar juntos** con otra persona tras el arreglo de la pantalla en blanco.
- [ ] Ajustes: comprobar el desplazamiento de las secciones Discord (si sigue) y Last.fm.
- [ ] Música local: subcarpetas, varios formatos (FLAC, M4A, OGG…), bibliotecas grandes y errores de
  lectura.
- [ ] Probar en el teléfono: importación de Spotify, Teatro con vídeo, textos largos y tamaño de letra
  del sistema grande.
- [ ] Aviso de Android 16 sobre «técnicas de refuerzo incompatibles»: no se ha vuelto a reproducir;
  investigar si reaparece.

Mejoras técnicas que afectan al uso:

- [ ] **APK de release:** la de depuración pesa unos 640 MB, tarda unos 5 min en instalarse y va sin
  optimizar (parte de la lentitud). Configurar una compilación release firmada (más pequeña y rápida).
- [ ] **Descifrado sin WebView** (QuickJS, ya está en las dependencias) para recuperar los clientes de
  YouTube que hoy se omiten en Android. Ahora todo suena por el cliente VISIONOS; si algún día falla,
  no hay alternativa.
- [ ] **Renovar la sesión de Google en segundo plano** en Android (en escritorio lo hace un WebView
  oculto, que aquí no es posible). Hoy, si caduca, hay que volver a iniciar sesión.

- [ ] **Batería:** Rust consulta el estado del reproductor Android cada 250 ms siempre, aunque no suene
  nada (`spawn_android_player_bridge` en `lib.rs`). Espaciar la consulta o pararla cuando está en pausa.
- [ ] Comprobar en el teléfono: desconectar auriculares pausa la música, una llamada la interrumpe y
  luego se reanuda, y el paso a la siguiente canción con la pantalla apagada.

## Fase 3 — Estética y accesibilidad móvil

- [ ] **Reproductor ampliado a pantalla completa:** portada grande, título y artista legibles, controles
  propios, deslizar hacia abajo para cerrar y a los lados para cambiar de canción. Cola, letra y
  comentarios como pestañas o paneles inferiores.
- [ ] **Barra compacta del reproductor más pequeña:** hoy ocupa tres filas (canción, progreso y
  controles). Dejarla en una sola fila con una línea de progreso fina, para dejar más pantalla al contenido.
- [ ] **Botón atrás de Android:** que cierre primero menús, diálogos y el reproductor ampliado antes de
  cambiar de página.
- [ ] **Áreas táctiles de 48 dp como mínimo** en iconos pequeños (tres puntos, me gusta, cerrar, chips).
- [ ] **Contraste:** los números de posición en «Artistas familiares» (morado sobre foto) y los textos
  grises sobre fondos oscuros.
- [ ] **Tamaño de letra del sistema:** que la interfaz respete la escala de fuente de Android sin romper
  la disposición.
- [ ] **Menús emergentes como hojas inferiores** (por ejemplo, el de iniciar sesión dentro de Más), que
  es lo que se espera en móvil.
- [ ] **Revisión visual general:** espaciados, jerarquía de títulos y coherencia entre pantallas
  (Inicio, Buscar, Biblioteca, Estadísticas, artista, álbum y playlist), en tema oscuro y claro.
- [ ] Etiquetas de accesibilidad en los botones que solo tienen icono (TalkBack).

## Ideas de funciones nuevas (sin decidir)

- [ ] **Abrir enlaces compartidos:** que LiMusic aparezca al pulsar «Compartir» en un enlace de YouTube o
  YouTube Music desde otra app, y lo reproduzca.
- [ ] **Temporizador de apagado:** parar la música tras X minutos o al acabar la canción.
- [ ] **Descargas sin conexión** de canciones o playlists.
- [ ] **Widget** de pantalla de inicio con la canción actual y los controles.
- [ ] **Android Auto.**

Ya comprobado y correcto: la notificación y la pantalla de bloqueo muestran portada, artista, controles
y barra de progreso.

## Fase 4 — Estadísticas compartidas con escritorio (descartada)

Descartada el 06/11/2026: se usa la app oficial de escritorio sin modificar, que no tiene importación
ni sincronización. Compartir estadísticas solo sería posible copiando a mano su archivo
`%APPDATA%\com.limusic.desktop\limusic.sqlite`, y no se quiere nada que no sea automático. Las playlists,
los «Me gusta» y la biblioteca de YouTube Music ya se comparten a través de la cuenta de Google.

Si en el futuro la app oficial añade exportación o sincronización, se puede retomar: el formato de
estadísticas es el mismo en ambas (tablas `listens` y `listen_songs`, esquema versión 2).
