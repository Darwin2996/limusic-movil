# Tareas pendientes — LiMusic móvil

Lista de trabajo viva. Se actualiza al empezar y terminar cada tarea; lo ya hecho se registra en
`CAMBIOS_MOVIL.md`. Cada fase termina con prueba en el teléfono y un commit propio.

Estado: `[ ]` pendiente · `[~]` en curso · `[x]` hecho · `[?]` necesita una decisión tuya

| Fase | Contenido | Por qué en este orden |
| ---- | --------- | --------------------- |
| 1 | Base y limpieza | La APK de release acelera cada prueba posterior y quitar lo sobrante evita rediseñarlo |
| 2 | Fallos | Lo que hoy está roto o se ve mal |
| 3 | Diseño y accesibilidad | Rediseño sobre una base ya limpia y sin fallos |
| 4 | Comodidad en el día a día | Detalles que hacen la app agradable de usar a diario |
| — | Ideas sin decidir | Funciones nuevas; se eligen cuando acaben las fases |

---

## Fase 1 — Base y limpieza

- [ ] **APK de release firmada:** la de depuración pesa unos 640 MB, tarda unos 5 min en instalarse y va
  sin optimizar (parte de la lentitud). Una release es mucho más pequeña y rápida. Va primero porque
  acorta cada ciclo de prueba de todas las fases.
- [ ] **Discord fuera de Android:** quitar del menú **Más** las dos entradas («Presencia en Discord» y
  «Discord»), la sección Discord de Configuración y su vista previa. En el backend, no arrancar el cliente
  de Discord en Android: ahora intenta conectar cada 15 s sin éxito.
- [ ] **Menú Más más corto:** «Abrir reproductor», «Cola», «Letra» y «Comentarios» repiten lo que ya hay
  en el reproductor ampliado.
- [ ] **Last.fm en el menú Más:** el texto «Last.fm» hace de icono y desalinea la fila; usar el icono.
- [ ] **Buscar actualizaciones:** es el actualizador de escritorio y no sirve en Android. Ocultarlo.
- [ ] **Opciones de escritorio en Configuración** (bandeja del sistema, inicio automático, iniciar
  minimizado, barra de título…): hoy se ocultan por **ancho de pantalla** y reaparecen en tableta o en
  horizontal. Ocultarlas por plataforma (`isAndroid`).
- [ ] **Minirreproductor de escritorio y atajos de teclado:** confirmar que no queda ningún acceso.
- [ ] **Batería:** Rust consulta el reproductor Android cada 250 ms siempre, aunque no suene nada
  (`spawn_android_player_bridge` en `lib.rs`). Espaciar la consulta o pararla en pausa.

## Fase 2 — Fallos

### Prioridad alta

- [ ] **Listas de canciones de playlist y álbum:** se muestra la tabla de escritorio comprimida en 406 px.
  Los títulos quedan en 3 o 4 letras («Whe…», «Can …»), el número de reproducciones se monta sobre el
  icono de me gusta, ese icono tapa el título en «Música que me gusta» y el artista se corta. La cabecera
  de columnas y «Elegir columnas» no tienen sentido en móvil. Hace falta una fila móvil: portada, título y
  artista a todo el ancho, y menú de tres puntos.
- [ ] **Botón atrás de Android:** cierra el menú o diálogo abierto **y además** retrocede de página
  (comprobado con el menú de una canción y con «Escuchar juntos»). Debe cerrar solo el menú o diálogo.
- [ ] **Reproductor ampliado:** no ocupa la pantalla; siguen visibles la barra compacta (con sus propios
  controles) y la navegación inferior. Como mínimo, que tape ambas (el rediseño completo está en la
  fase 3).

### Barra de estado y elementos solapados

- [ ] **Contenido bajo la barra de estado:** al desplazar, todo pasa por debajo del reloj y los iconos sin
  fondo (Inicio, artista, playlist). Quedan fijos detrás del reloj la barra de chips de Inicio, el
  buscador «Buscar en este álbum» y la cabecera de la tabla. Añadir margen del área segura y un fondo o
  degradado bajo la barra de estado.
- [ ] **Inicio:** con la sesión iniciada, la foto de perfil queda encima del botón de Historial.
- [ ] **Inicio:** asoma un botón morado flotante detrás de la pestaña «Más» de la barra inferior.
- [ ] **Configuración:** el título de la sección sale dos veces, y por encima del panel se ve la página
  de detrás. Además se abre en la última sección visitada (a menudo, Discord).
- [ ] **Página de artista:** en «Canciones principales», me gusta, no me gusta y menú ocupan tanto que el
  título se corta y «47 M reproducciones» se parte en dos líneas. Pasar me gusta/no me gusta al menú.
- [ ] **Páginas de detalle sin botón de volver** (artista, álbum, playlist): añadir una flecha visible.
- [ ] **Buscar:** el texto de ejemplo del buscador se corta («…, artista»).

### Textos y números

- [ ] **10 textos sin traducir al español:** `player.scroll_to_end` («Scroll to bottom»),
  `player.fullscreen_video`, `player.exit_fullscreen_video`, `library.suggestions_show`,
  `library.suggestions_hide`, `library.suggestions_hidden`, `settings.general.play_on_start` (+ `_hint`),
  `settings.themes.now_playing_pill` (+ `_hint`).
- [ ] **Números mal formateados:** «2768 M reproducciones» (página de artista) y «0+ horas» en la cabecera
  de una playlist de 6 canciones (revisar si viene así de YouTube).
- [ ] **Nombre en el lanzador:** la app aparece como «limusic», en minúsculas. Cambiarlo a «LiMusic».

### Riesgos técnicos

- [ ] **Descifrado sin WebView** (QuickJS, ya en las dependencias) para recuperar los clientes de YouTube
  que hoy se omiten en Android. Todo suena por VISIONOS; si falla, no hay alternativa.
- [ ] **PoToken no disponible en Android** («BotGuard is not available in this Android build»). Si YouTube
  lo empieza a exigir, la reproducción podría fallar con errores 403. Vigilar.
- [ ] **Renovar la sesión de Google en segundo plano:** hoy, si caduca, hay que volver a iniciar sesión.

### Pruebas pendientes en el teléfono

- [ ] **Escuchar juntos** con otra persona, tras el arreglo de la pantalla en blanco.
- [ ] **Modo Teatro**, con y sin vídeo.
- [ ] **Audio:** desconectar auriculares pausa; una llamada interrumpe y luego se reanuda; botones de
  auriculares Bluetooth; paso a la siguiente canción con la pantalla apagada.
- [ ] **Música local:** subcarpetas, varios formatos (FLAC, M4A, OGG…), bibliotecas grandes y errores.
- [ ] **Importación de Spotify**, textos largos y tamaño de letra del sistema grande.
- [ ] **Sin conexión o con mala red:** qué ve el usuario y si se recupera solo al volver la red.
- [ ] Aviso de Android 16 sobre «técnicas de refuerzo incompatibles»: investigar si reaparece.

## Fase 3 — Diseño y accesibilidad

### Reproductor

- [ ] **Reproductor ampliado a pantalla completa:** portada grande, título y artista legibles, controles
  propios, deslizar hacia abajo para cerrar y a los lados para cambiar de canción. Cola, letra y
  comentarios como pestañas o paneles inferiores. El botón atrás lo cierra antes de cambiar de página.
- [ ] **Barra compacta en una sola fila:** hoy ocupa tres (canción, progreso y controles). Una fila con
  portada, título, reproducir/pausa y siguiente, más una línea de progreso fina.

### Menús y navegación

- [ ] **Menús como hojas inferiores:** el menú de una canción (13 opciones en un menú emergente que tapa
  el reproductor), el de iniciar sesión dentro de Más y el resto de menús emergentes.
- [ ] **Revisión visual general:** espaciados, jerarquía de títulos y coherencia entre Inicio, Buscar,
  Biblioteca, Estadísticas, artista, álbum y playlist, en tema oscuro y claro.

### Accesibilidad

- [ ] **Áreas táctiles de 44–48 px como mínimo.** Medidas con la auditoría:
  - Nombres de artista en todas las filas y tarjetas: 16 px de alto.
  - Selectores de día y hora en Estadísticas: 16 px; pestañas de periodo: 29 px.
  - Buscadores: Inicio 36 px; «Buscar en tu historial» y «Buscar en este álbum»: 20 px.
  - Quitar una búsqueda reciente (×): 24 px; «Limpiar»: 16 px.
  - Barra de progreso del reproductor: 28 px; título de la canción en la barra: 20 px.
  - Reproducir en tarjetas: 36 px; «Importar» y «Nueva playlist»: 32 px.
  - Ordenar, dirección de orden y «Elegir columnas»: 28–32 px.
- [ ] **Textos demasiado pequeños:** barra inferior (10,4 px), insignia «Experimental» (10 px) y
  «usuarios mensuales» en artistas relacionados (11 px).
- [ ] **Contraste:** números de posición en «Artistas familiares» (morado sobre foto), textos grises
  sobre fondo oscuro, y «suscriptores»/«usuarios mensuales» sobre la foto del artista (añadir degradado).
- [ ] **Estados que solo se distinguen por el color** (pestaña activa, «me gusta» marcado): añadir forma,
  relleno o texto, para personas con daltonismo.
- [ ] **Tamaño de letra del sistema:** respetar la escala de fuente de Android sin romper la disposición.
- [ ] **Etiquetas para TalkBack** en los botones que solo tienen icono.

## Fase 4 — Comodidad en el día a día

- [ ] **Pulsación larga en una canción** abre su menú (hoy solo el botón de tres puntos, pequeño).
- [ ] **Gestos en la barra compacta:** deslizar a los lados para cambiar de canción y hacia arriba para
  abrir el reproductor.
- [ ] **Volver a pulsar la pestaña activa** de la barra inferior sube al principio de la página.
- [ ] **Conservar la posición de desplazamiento** al volver atrás a una lista o página.
- [ ] **Deslizar hacia abajo para recargar** en Inicio y Biblioteca.
- [ ] **Calidad distinta en Wi-Fi y en datos móviles:** hoy hay un único ajuste de calidad.
- [ ] **Ahorro de batería de MIUI/Android:** la app no está excluida, y Xiaomi suele cerrar apps en segundo
  plano, lo que corta la música. Al primer uso, explicar cómo excluirla y ofrecer el ajuste del sistema.
- [ ] **Retomar la última sesión:** al abrir la app, recuperar la cola y la posición de la última canción
  (comprobar qué hace hoy la opción «reproducir al iniciar»).
- [ ] **Bloquear la app en vertical en teléfonos** (decidido el 06/11/2026): hoy gira libremente a
  horizontal, que no está diseñada.

## Ideas sin decidir

- [ ] **Abrir enlaces compartidos:** que LiMusic aparezca al pulsar «Compartir» en un enlace de YouTube o
  YouTube Music desde otra app, y lo reproduzca.
- [ ] **Temporizador de apagado:** parar la música tras X minutos o al acabar la canción.
- [ ] **Me gusta desde la notificación** y la pantalla de bloqueo.
- [ ] **Descargas sin conexión** de canciones o playlists.
- [ ] **Widget** de pantalla de inicio con la canción actual y los controles.
- [ ] **Android Auto.**
- [ ] **Avisar de versiones nuevas de la APK** (por ejemplo, desde las releases de GitHub).

## Ya comprobado y correcto

- Pantalla en blanco al reproducir: arreglada (commit `62262fb`).
- Inicio de sesión con Google: funciona con la página oficial de Google (commit `06529af`).
- La notificación y la pantalla de bloqueo muestran portada, artista, controles y barra de progreso.
- La app no registra errores en los logs durante el uso normal.

## Descartado

- **Estadísticas compartidas con la app de escritorio** (06/11/2026): la app oficial no tiene importación
  ni sincronización, y solo sería posible copiando a mano su archivo
  `%APPDATA%\com.limusic.desktop\limusic.sqlite`. No se quiere nada que no sea automático. Las playlists,
  los «Me gusta» y la biblioteca ya se comparten a través de la cuenta de Google. Si la app oficial añade
  sincronización, se puede retomar: el formato de estadísticas es el mismo (tablas `listens` y
  `listen_songs`, esquema versión 2).
- **Respetar la opción de Android de quitar animaciones** (06/11/2026): no se implementa.
