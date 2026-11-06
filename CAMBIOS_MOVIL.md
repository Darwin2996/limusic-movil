# Cambios de adaptación móvil

Registro acumulativo de correcciones para la aplicación Android y el uso en pantallas táctiles.
El repositorio recibido no incluye metadatos de Git; este archivo registra los cambios aplicados
durante esta revisión y distingue el código implementado de lo que falta validar en un teléfono.

## Implementado en esta revisión

- [x] Añadido un menú móvil **Más** con acceso a cuenta/perfiles, Historial, abrir enlace,
  Escuchar juntos, Teatro, Discord, ajustes de Last.fm y Configuración. La barra inferior conserva
  Inicio, Buscar, Biblioteca y Estadísticas; las listas están dentro de Biblioteca.
- [x] Añadidos accesos móviles directos a reproductor ampliado, cola, letras y comentarios (estos
  últimos solo cuando la canción no es un archivo local).
- [x] Cambiadas las pestañas horizontales de Configuración por un selector móvil que muestra todas
  las secciones sin exigir deslizar una tira fuera de pantalla.
- [x] Vuelto a mostrar los tiempos de reproducción en móvil. A petición del usuario, se retiró el
  deslizador de volumen del reproductor y Android inicia con la ganancia de LiMusic al 100 %; el
  volumen general se ajusta con las teclas del teléfono.
- [x] Separadas las acciones flotantes de selección múltiple, sugerencias y avisos de las barras
  inferiores del teléfono, con márgenes para el área segura de Android.
- [x] Añadidos botones para subir y bajar elementos en la cola, en el orden de fuentes de letras y
  en el orden de secciones de Inicio; el arrastre y los atajos existentes siguen disponibles.
- [x] Adaptado el modo Teatro en móvil: ahora aparecen controles de letras y cola en la cabecera,
  aunque el diseño de escritorio oculte la barra superior. Letras y cola se muestran como paneles
  superpuestos de altura limitada por encima del dock, sin comprimir la portada. En el teléfono se
  verificó cambiar entre ambas vistas y desplazar táctilmente la cola.
- [x] Añadida selección de día y hora en Estadísticas para consultar el tiempo exacto sin depender
  de tooltips al pasar el puntero.
- [x] Ajustados en móvil tamaños de toque y visibilidad de acciones de tarjetas, listas recientes,
  accesos directos y carruseles que antes dependían de hover.
- [x] Mostrado el recuento de reproducciones bajo el artista en las filas móviles que antes lo
  ocultaban.
- [x] Reducidos márgenes y algunos tamaños de portada/título en páginas de artista, álbum y
  playlist; reorganizados los botones y filtros de sus cabeceras para anchos pequeños.
- [x] Reorganizados los campos de edición de playlists en vertical en móvil.
- [x] Añadidos límites de altura y desplazamiento táctil a los diálogos generales y de importación;
  reforzado el desplazamiento vertical de Configuración.
- [x] En Android, **Añadir carpeta** usa el selector SAF del sistema. Los archivos de audio
  compatibles se copian a almacenamiento privado de LiMusic para que el escáner Rust y el
  reproductor puedan leerlos. Se informa que la importación ocupa espacio; se muestra actividad
  durante la selección/copia y se presentan errores comprensibles. Al quitar la carpeta de la
  biblioteca, también se elimina la copia importada. Se corrigió la consulta del nombre de la
  carpeta: Android entrega un URI de árbol y la consulta de metadatos debe hacerse sobre su
  documento raíz.
- [x] El reproductor Android admite fuentes HTTP y archivos locales. Se añadió la fuente de datos
  predeterminada de Media3 alrededor de HTTP para enrutar rutas locales a `FileDataSource`.
- [x] En Android, reproducir una canción ya no abre automáticamente el reproductor ampliado:
  WebView puede anunciar un ancho CSS de escritorio aunque el dispositivo sea un teléfono. También
  se apilaron en vertical los datos de canción y sus acciones para que el título no se comprima en
  una columna estrecha al abrir manualmente el reproductor. En el teléfono se probó iniciar una
  canción desde Inicio y abrir el reproductor ampliado manualmente; la interfaz permaneció visible,
  el reproductor avanzó y el título/artista/álbum se dispusieron en vertical.
- [x] Se descartó el renderizado por software porque hacía la app lenta. Con aceleración gráfica
  normal, Android reconstruye la superficie al confirmar el primer cuadro visual y al cargar una
  canción: se separa y vuelve a adjuntar el mismo WebView, sin recargar la página ni reiniciar el
  proceso. También se pide un repintado al recuperar el foco.
- [x] **Pantalla en blanco intermitente:** se reprodujo una pantalla negra con la página viva en el
  DOM. Al tocar Reproducir, volver a adjuntar el WebView restauró la interfaz inmediatamente y la
  música siguió avanzando. También quedó visible desde el primer cuadro de un arranque en frío; se
  probaron tres cargas seguidas sin regresión de rendimiento en el teléfono conectado.
- [x] En el reproductor ampliado, los nombres largos de artistas y álbumes pueden ocupar varias
  líneas en vertical en lugar de cortarse al final.
- [x] En música local, los botones de añadir/volver a escanear se reorganizan para móvil y el botón
  de quitar carpeta tiene un área táctil mayor.
- [x] En el menú móvil **Más**, la fila de cuenta ahora incluye una etiqueta visible (“Cuenta”),
  para que el icono de inicio de sesión/cambio de perfil no sea la única pista de su función.
- [x] Comprobado en el teléfono el diálogo **Abrir enlace** con el teclado Android abierto: el
  campo y el botón “Abrir” permanecen visibles por encima del teclado.
- [x] Unificada la petición de volumen fijo en Android: la ganancia interna se fuerza al 100 % al
  iniciar y al recibir eventos, los gestos/atajos de volumen no la alteran y el modo Teatro ya no
  muestra el deslizador ni el botón de silencio. El volumen se regula con los botones físicos del
  dispositivo.
- [x] Android registra el proveedor criptográfico de Rustls antes de inicializar servicios y
  clientes HTTPS, evitando que la app se cierre al arrancar en una compilación limpia.
- [x] Ocultada en Android la pestaña de atajos globales: el backend informa que no son compatibles,
  por lo que ofrecer su activación permitía configurar una función que no podía operar. Confirmado
  en el selector de secciones de Configuración del teléfono.
- [x] Comprobado en el teléfono que Apariencia se desplaza verticalmente y que el selector táctil
  de color ofrece paleta de tono y campo hexadecimal; no requiere el cuentagotas del navegador.

## Pendiente de resolución o comprobación

- [ ] **Comprobar en Ajustes gestos y pestañas con otras configuraciones:** en esta sesión se
  confirmó que el panel general y Apariencia sí desplazan verticalmente en el teléfono; faltan las
  secciones Discord y Last.fm.
- [ ] **Ampliar pruebas de música local:** se importó una carpeta de Android con un MP3, apareció
  indexado y se reprodujo correctamente. Falta verificar subcarpetas, varios formatos, bibliotecas
  grandes y errores de lectura.
- [ ] **Verificar en el teléfono** importación Spotify, barras flotantes, Teatro con vídeo, textos
  largos y tamaños de fuente grandes. Ya se comprobó el selector de letras/cola y el desplazamiento
  táctil de la cola, además de que el diálogo Abrir enlace conserva visibles su campo y botón con
  el teclado Android abierto.
- [ ] El minirreproductor de ventana de escritorio no es una ventana flotante Android; el menú
  móvil da acceso al reproductor ampliado y la barra compacta persistente. No se ha implementado
  Picture-in-Picture del sistema.
- [ ] Los chips de Inicio y Buscar conservan el desplazamiento horizontal; revisar en pantalla si
  necesitan una señal visual adicional.
- [ ] Verificar el selector de carpeta y lectura de formatos distintos de MP3 en más dispositivos
  Android/proveedores SAF.

## Límites de validación

Validación realizada: `pnpm run check` (sin errores ni avisos) y compilación del frontend de producción,
compilación Rust para `aarch64-linux-android`,
compilación de APK de depuración e instalación/arranque por ADB en un Xiaomi con Android
16. La APK de producción con aceleración gráfica normal y recuperación de superficie al primer
cuadro/carga de canción se compiló e instaló en el teléfono. Se capturó el estado en negro y se tocó
Reproducir: la interfaz se recuperó de inmediato; tres cargas seguidas conservaron la UI y el progreso
avanzó. En un arranque en frío la interfaz también quedó visible antes de tocarla. La última APK
instalada incluye la etiqueta de Cuenta, el ajuste tipográfico de nombres largos, el volumen fijo sin
slider, el selector móvil de letras/cola de Teatro y la retirada de la pestaña de atajos globales
incompatibles; arrancó, el progreso avanzó con Teatro abierto y se desplazó la cola táctilmente. La
compilación limpia mostró una falta de proveedor Rustls en Android, que se corrigió inicializándolo
al arranque. Antes de estos últimos cambios se seleccionó una
carpeta SAF de prueba y se comprobó la copia de un MP3 a
almacenamiento privado, su indexación en la biblioteca y su reproducción: el contador avanzó de
0:00 a 0:05 sin errores recientes de Media3. Se eliminó del teléfono la copia privada y también la
carpeta temporal de prueba. Se verificaron Ajustes desplazándose verticalmente, el menú Más y su
fila Last.fm, además de las vistas móviles mencionadas arriba. La importación local usa
almacenamiento privado: los archivos no son accesibles desde otras aplicaciones y ocupan espacio
hasta quitar la carpeta desde LiMusic. La reproducción en línea y el resto de pruebas pendientes
siguen sin confirmarse. Para esta compilación se usaron copias temporales dentro de `.codex-tmp` de
tres plugins Tauri, porque los scripts Android de la caché de Cargo no permiten escribir sus archivos
generados; `Cargo.lock` se restauró al terminar y no se cambiaron las dependencias del proyecto.

- [x] Sustituido el selector nativo de apartados de Ajustes en móvil por un desplegable propio con iconos, título y descripción; el apartado activo queda destacado y las opciones usan los colores de la apariencia seleccionada. El menú admite desplazamiento y controles táctiles amplios. Verificados `pnpm run check` (sin errores ni avisos) y `pnpm run build`. APK compilada, instalada como actualización conservando los datos y comprobada en el teléfono: el menú abre, resalta la sección activa y al seleccionar Apariencia actualiza el título y el icono.

## Cambios de esta revisión — Ajustes y acceso móvil

- [x] Discord: en móvil la vista previa de Rich Presence queda plegada inicialmente para dar espacio a los controles. Se puede abrir desde «Previsualización»; en pantallas grandes la vista previa lateral se mantiene. Los controles ajustan su disposición y el panel conserva su desplazamiento.
- [x] Scrobbleo: la vista previa también queda plegada inicialmente en móvil. Se reorganizaron las filas para que etiquetas, selectores y controles no se compriman en paralelo en pantallas estrechas; se mantiene la vista previa de escritorio.
- [x] Teatro: en vertical, al abrir letra o cola, su contenido ocupa un panel dentro del flujo de la página debajo de la portada e información de la canción. Ya no se superpone sobre la canción; la portada reduce su tamaño para compartir la pantalla.
- [x] Música local: la pestaña aparece junto a «Todo». Cuando aún no hay carpetas, se explica qué hacer y se muestra el botón «Añadir carpeta de música» de forma destacada; al haber carpetas, se conserva el control de añadir en el encabezado.
- [x] Verificado `pnpm run check` (0 errores, 0 avisos), `pnpm run build`, compilación e instalación de la APK actualizada. En el teléfono se revisaron Discord, Scrobbleo, la pestaña y el estado vacío de Música local, así como Teatro con la cola y con la letra abierta.
- [ ] Inicio de sesión de Google: sigue pendiente. El flujo actual inicia sesión en un WebView incrustado y Google bloquea algunos navegadores embebidos. Abrir el navegador externo por sí solo no entrega a LiMusic las cookies de YouTube Music que usa su sesión actual; requiere un flujo de autenticación compatible y adaptar el intercambio de sesión. No se simuló una redirección como si resolviera el inicio de sesión.
- [ ] Aviso de Android 16: la captura menciona técnicas de refuerzo incompatibles, pero no se reprodujo durante esta revisión. El APK comprobado declara `targetSdk 36`, contiene solo `libapp_lib.so` para arm64, tiene sus segmentos ELF con alineación `0x4000` (16 KB) y pasa `zipalign -c -P 16 -v 4`. El manifiesto compilado no declara `android.app.16kb_pages_enabled`. Los registros recientes no muestran una excepción de notificaciones/seguridad de LiMusic. Esto descarta el desajuste habitual de páginas de 16 KB en el APK comprobado, pero no identifica el origen del aviso original; no se modificó el manifiesto a ciegas.

## Inicio de sesión Google — flujo OAuth Android

> **Sustituido** por el inicio de sesión con la página de Google (sección «Inicio de sesión Google — página
> de Google en pantalla nativa»). YouTube Music rechaza los tokens OAuth de clientes de terceros con
> HTTP 400 desde agosto de 2025, así que este flujo no podía funcionar y su código se eliminó.

- [x] Se sustituyó para Android la apertura del formulario de Google dentro del WebView de LiMusic por la pantalla de autorización nativa de Google Play Services. Las credenciales se gestionan en la interfaz de Google/Android y no se introducen en el WebView de LiMusic.
- [x] Se añadieron las autorizaciones de YouTube y del perfil/correo, el callback de autorización, la selección de cuenta al añadir otra y la obtención del correo verificado para conservar la separación de cuentas de LiMusic.
- [x] Se adaptó el transporte InnerTube y la descarga de subidas privadas para enviar el token OAuth como `Authorization: Bearer`; las sesiones de escritorio basadas en cookies mantienen su transporte anterior. La renovación Android solicita a Play Services una autorización actualizada para la misma cuenta.
- [x] Compiló el plugin Kotlin Android (`:app:compileArm64DebugKotlin`) y pasó `cargo check` y `cargo build` Rust para `aarch64-linux-android`. El APK arm64 debug se empaquetó correctamente.
- [x] APK arm64 debug corregido e instalado por ADB sobre la app existente, conservando los datos. Se verificó en el Xiaomi que la pantalla de Inicio carga desde los recursos incluidos y que no aparece el error de `localhost`; no se aceptó ni se introdujo ninguna credencial o consentimiento de usuario.
- [ ] Falta validar la autorización real con una cuenta Google en el teléfono. El paso final requiere que tú elijas tu cuenta y aceptes el consentimiento de Google.
- [ ] Confirmar en Google Cloud que el proyecto OAuth está en modo de pruebas/publicación correcto y que la cuenta usada está añadida como usuario de prueba si la aplicación sigue en pruebas. El cliente Android se vincula por nombre de paquete y SHA-1 de firma; Play Services resuelve el cliente a partir de la app instalada.
- [ ] Validar que YouTube Music/InnerTube acepta el token Bearer para `account_menu`, biblioteca y reproducción. La compilación no demuestra por sí sola compatibilidad del backend con todas las operaciones autenticadas.
- [x] El primer empaquetado manual abrió `localhost:5183` y mostró una pantalla vacía porque no llevaba la feature `custom-protocol`; se regeneró la biblioteca con `tauri/custom-protocol`, se reinstaló el APK y se verificó el Inicio.
- [ ] La compilación automatizada de Gradle no pudo llamar a `node tauri android android-studio-script` porque el checkout no contiene ese ejecutable. Se generó la biblioteca con Cargo y se empaquetó excluyendo esa tarea; durante la comprobación se usaron copias temporales de tres plugins Tauri bajo `.codex-tmp` para evitar escribir en el caché de Cargo. `Cargo.lock` se restauró sin conservar parches ni cambios de dependencia.

## Pantalla en blanco al reproducir — causa raíz

- [x] **Causa real encontrada:** en Android, wry (el motor de WebView de Tauri) solo admite un WebView por
  actividad. Al reproducir una canción que necesitaba descifrar su URL, el backend creaba el WebView
  "oculto" de descifrado (`limusic-cipher`); en Android `visible(false)` no tiene efecto y wry llama a
  `setContentView` con el nuevo WebView, que sustituía a la interfaz por una página vacía en blanco y
  además recibía los mensajes de Rust destinados a la app. La música seguía sonando, pero no se podía
  tocar nada. Lo mismo ocurría al escuchar en conjunto, porque las canciones del anfitrión pasan por el
  mismo descifrado. Comprobado con DevTools: había dos páginas y la visible era `limusicharness.localhost`.
- [x] En Android ya no se crea el WebView de descifrado (`src-tauri/src/cipher/mod.rs`): el reproductor se
  trata como no descifrable y el orquestador pasa a los clientes directos (p. ej. VISIONOS, mismo itag 251).
- [x] El refresco de sesión por cookies tampoco crea su WebView oculto en Android (`src-tauri/src/session.rs`).
- [x] Retirado el parche que separaba y volvía a adjuntar el WebView en cada carga de canción
  (`PlayerPlugin.kt`), que causaba el parpadeo al elegir canción.
- [x] Verificado en el Xiaomi: la reproducción de un álbum que antes dejaba la pantalla en blanco mantiene
  la interfaz visible, cuatro saltos de canción seguidos y la navegación responden, y DevTools muestra un
  único WebView.
- [ ] Probar «Escuchar juntos» con otra persona en el teléfono.
- [ ] Mejora futura: descifrar en Android sin WebView (p. ej. con QuickJS, que ya está en el árbol de
  dependencias) para recuperar los clientes que lo necesitan.

## Inicio de sesión Google — página de Google en pantalla nativa

- [x] **Por qué no funcionaba:** el flujo OAuth anterior obtenía el token, pero YouTube Music lo rechazaba
  con HTTP 400 en `account_menu`. Desde el 29 de agosto de 2025 YouTube Music no acepta tokens OAuth de
  clientes de terceros (ytmusicapi #813, sin solución). El login original de escritorio fallaba en Android
  por el mismo motivo que la pantalla en blanco: abría un segundo WebView de Tauri que sustituía la app.
- [x] Nueva pantalla nativa `GoogleLoginActivity` que abre la página oficial de Google
  (`accounts.google.com`) y, al llegar a music.youtube.com, devuelve las cookies de youtube.com a Rust,
  que inicia sesión por el mismo camino que escritorio. Muestra arriba una barra con candado y dominio para
  que se vea que se escribe en la web de Google. Funciona el autocompletado de contraseñas de Android.
  «Añadir cuenta» borra antes la sesión de Google de esa pantalla, como en escritorio.
- [x] Eliminado el código OAuth: comando `authorizeGoogle`, dependencia `play-services-auth`, token
  Bearer en `transport.rs` y `orchestrator.rs`, y el refresco OAuth de `session.rs`. El proyecto de Google
  Cloud ya no es necesario.
- [x] Verificado en el Xiaomi: el usuario inició sesión y la Biblioteca cargó sus playlists y «Música que
  me gusta», sin errores 400/401/403 en los registros.
- [ ] En Android la sesión no se renueva en segundo plano (escritorio usa un WebView oculto que aquí no es
  posible). Si caduca, hay que volver a iniciar sesión; si Google mantiene su sesión, entra directamente.

## Fases 1–4 de TAREAS_MOVIL.md (06/11/2026)

Detalle y estado de cada tarea en `TAREAS_MOVIL.md`. Resumen de lo aplicado:

- **APK optimizada:** Rust en modo release (LTO, sin símbolos) dentro del mismo paquete instalado, para
  conservar la sesión y los datos. La librería nativa baja de ~600 MB a 72 MB y la app va más fluida.
- **Limpieza:** Discord fuera de Android (menú, Configuración y el cliente que reintentaba cada 15 s);
  menú Más sin entradas repetidas; icono de Last.fm; sin actualizador ni aviso de versiones de escritorio;
  opciones de escritorio ocultas por plataforma; atajos de teclado ocultos; consulta del reproductor cada
  1 s en pausa (antes 250 ms siempre).
- **Botón atrás de Android:** cierra primero menús, diálogos, Teatro y el reproductor ampliado, y solo
  después cambia de página; en la primera página manda la app a segundo plano. Usa el evento
  `back-button` de Tauri, que era quien navegaba atrás sin mirar si había algo abierto.
- **Listas de álbum y playlist:** por debajo de 640 px ya no se usa la tabla de escritorio (que dejaba el
  título en 3–4 letras); en móvil me gusta/no me gusta pasan al menú y solo se ve la valoración dada.
- **Reproductor:** plegado ocupa una fila con línea de progreso; abierto ocupa la pantalla con la barra de
  progreso y los controles completos, sin la navegación. Gestos de deslizar en la barra plegada. En
  Android las pestañas cola/letra/comentarios están siempre activas.
- **Menús como hojas inferiores** con filas de 48 px; pulsación larga en canciones y tarjetas abre el menú.
- **Barra de estado:** las páginas, el Teatro y el reproductor ampliado respetan el área segura superior e
  inferior. Inicio apila saludo y buscador (la foto de perfil se montaba sobre Historial). «Volver
  arriba» ya no queda detrás de la navegación. Flecha de volver en artista, álbum, playlist, lista e
  historial.
- **Accesibilidad:** áreas táctiles ampliadas (Estadísticas, búsquedas recientes, buscadores de lista,
  tarjetas, Biblioteca, barra de progreso), etiquetas de la barra inferior a 12 px, píldora en la pestaña
  activa, contraste en la cabecera de artista y en «Artistas familiares», y etiquetas de TalkBack de las
  filas traducidas (estaban en inglés fijo).
- **Textos:** 10 traducciones al español que faltaban; nombre «LiMusic» en el lanzador; título de sección
  de Configuración ya no sale dos veces.
- **Comodidad:** aviso (una vez) y ajuste para excluir LiMusic del ahorro de batería; «Calidad con datos
  móviles» separada de la de Wi-Fi; volver a pulsar la pestaña activa sube al principio; app bloqueada en
  vertical en teléfonos.
- [x] Verificado en el Xiaomi: álbum, artista, Inicio, menú Más, Configuración (General y Reproducción),
  menú de canción como hoja, botón atrás con menú/diálogo/reproductor/Teatro, reproductor ampliado y
  Teatro sin vídeo. `pnpm run check` sin errores ni avisos y `menu.check` correcto.
- [x] Gestos de la barra: no respondían porque el navegador se quedaba el gesto (`pointercancel`); con
  `touch-action: none` en la barra plegada funcionan. Probado en el Xiaomi en ambas direcciones.
- [x] El usuario confirmó el aviso de batería y «Escuchar juntos» funcionando.
- [ ] Sin probar: la calidad con datos móviles en una reproducción.
- [x] Animación al cambiar de canción: en la barra del reproductor, la portada y el título nuevos entran
  deslizándose 24 px (200 ms) desde el lado del gesto, o desde la derecha si cambia sola o con los
  botones. Medido en el Xiaomi con `getAnimations()`. Se desactiva con la opción de reducir animaciones.

## Sesión, reproductor y textos (06/11/2026)

- [x] **Renovación de la sesión de Google en Android:** cuando YouTube rechaza la sesión, un WebView nativo
  que nunca se muestra (`GoogleSessionRefresher.kt`) carga music.youtube.com con las cookies del inicio
  de sesión y Rust recoge las renovadas, con la misma comprobación de cuenta que escritorio. Antes, en
  Android, una sesión caducada obligaba a iniciar sesión de nuevo. No se puede provocar un 401 en una
  prueba sin estropear la sesión; la app arranca sin errores.
- [x] Reproductor ampliado: la portada ocupa el 60 % del alto (antes la mitad); la cola sigue debajo.
- [x] Textos pequeños en móvil: insignia «Experimental» a 11 px y subtítulos de tarjetas pequeñas a 12 px.
- [x] Riesgo del descifrado: confirmado en el código que, si fallan los clientes directos, el orquestador
  recurre a rustypipe (descifrado propio con QuickJS, compila para Android).
- [ ] Sin probar: Teatro con vídeo (sonaba música local, sin vídeo).

## Funciones nuevas y publicación (06/11/2026)

- [x] **Temporizador de apagado** (luna en el reproductor ampliado): 15–60 min o al acabar la canción. La
  cuenta atrás va en Rust (`sleep.rs`), así que funciona con la pantalla apagada.
- [x] **Deslizar para recargar** en Inicio y Biblioteca.
- [x] **Descargas sin conexión:** menú de canción, botón en álbum y playlist y Biblioteca ▸ Descargas. Los
  archivos van en `downloads/` de los datos de la app y `AppState::resolve` los reproduce antes de ir a
  la red (`downloads.rs`).
- [x] **Actualizaciones desde la app:** el aviso consulta las releases públicas de
  `Darwin2996/limusic-movil`, descarga la APK y abre el instalador de Android (siempre pide confirmar).
  Las versiones se publican con `scripts/android-release.ps1 -Version X.Y.Z`, que tiene que ejecutarse
  en este ordenador: la APK se firma con su clave de depuración y Android solo acepta actualizaciones
  firmadas con la misma clave.
- [x] Avisos flotantes ajustados a la barra del reproductor de una fila.
- [ ] Sin probar en el teléfono (el móvil no estaba conectado): temporizador, deslizar para recargar,
  descargas e instalación de actualizaciones.
- [x] **Publicado** en https://github.com/Darwin2996/limusic-movil (público, código en `main`) con la release
  **v1.4.1** y su APK. Comprobado: la API de releases devuelve la APK, se ofrece a la 1.4.0-rc.1 y no a la
  1.4.1, y la descarga funciona. La APK está firmada con la misma clave que la instalada
  (SHA-1 `46:7D:79…`). Antes de publicar se reescribió el historial para quitar capturas y registros de
  Codex y usar la dirección anónima de GitHub como autor.

## Enlaces compartidos, «me gusta» en la notificación y widget (06/11/2026)

- [x] **Abrir enlaces compartidos:** LiMusic aparece en «Compartir» (texto) y en «Abrir con» de enlaces de
  YouTube y YouTube Music; el enlace se abre como en «Abrir enlace» (`SharedLinks.kt`, `LinkDialog.svelte`).
- [x] **«Me gusta» en la notificación y la pantalla de bloqueo:** botón de corazón de Media3 que puntúa la
  canción a través de la interfaz (como su propio corazón) y se rellena según la valoración.
- [x] **Widget de pantalla de inicio** (`PlayerWidget.kt`): portada, título, artista y
  anterior/reproducir/siguiente; si la app no está abierta, la abre.
- [x] Compila (Rust, Kotlin y la interfaz). [ ] Sin probar en el teléfono.

## Fuentes, temas y estilos (06/11/2026)

- [x] **10 fuentes nuevas** (Fontsource): Inter, Manrope, Space Grotesk, Nunito, Quicksand, Comfortaa,
  Lora, Playfair Display, Atkinson Hyperlegible (de lectura fácil) y JetBrains Mono. El selector las
  muestra con su propia tipografía.
- [x] **5 temas de color nuevos**, con modo claro y oscuro: Ocean, Sunset, Pastel, Forest y High contrast.
  Los cuatro primeros se generan a partir de un tono; el de alto contraste está hecho a mano.
- [x] **Estilos de interfaz** (Suave, Moderno, Minimalista, Clásico y Cuadrado): redondez y pareja de fuentes
  de un toque, sobre cualquier tema; después se puede ajustar cada parte.
- [x] `@sveltejs/kit` y `adapter-static` fijados a 2.69.1 y 3.0.10: estaban como `latest` y una instalación
  los subía a versiones mayores nuevas.

## Sistema de amigos (06/11/2026)

- [x] Basado en Supabase (gratuito, sin servidor propio): cada móvil entra con una cuenta anónima y elige
  un @usuario; no se usa nada de Google. La privacidad se aplica en la base de datos con row level
  security (`supabase/amigos.sql`).
- [x] Más ▸ Amigos: perfil (nombre, bio, color), añadir por @usuario, aceptar o rechazar solicitudes, lista
  con lo que escucha cada amigo en directo, ficha del amigo con «Reproducir» su canción e «Invitar a
  escuchar juntos» (crea la sala si hace falta y le envía el código). Al invitado le aparece un aviso con
  «Unirme», en cualquier pantalla. «Compartir lo que escucho» se puede apagar.
- [x] El formato de invitación de Escuchar juntos se movió a `lt.svelte.ts` para usar el mismo en ambos.
- [x] Proyecto de Supabase conectado: tablas creadas, cuentas anónimas activas y escritura sin sesión rechazada por las reglas (comprobado). Publicado en la **v1.4.2**.
- [ ] Pendiente: probarlo entre dos móviles.

## Progreso de la actualización (06/11/2026)

- [x] Al pulsar **Actualizar**, el aviso muestra una barra de progreso con el porcentaje y los MB
  descargados, y «Abriendo el instalador…» al terminar. Antes solo salía un aviso fugaz y parecía
  que no pasaba nada. La APK lleva la cuenta de bytes (`ApkUpdater`) y la interfaz la consulta cada
  300 ms (`apk_update_progress`). Publicado en la **v1.4.3**; la barra se ve a partir de la
  siguiente actualización, porque quien instala la 1.4.3 desde la 1.4.2 usa todavía el aviso antiguo.

## Auriculares, Bluetooth y coche (06/11/2026)

- [x] Al quitar los auriculares o desconectar unos cascos Bluetooth, un altavoz o el coche, la música
  se pausa en vez de seguir por el altavoz del teléfono (`setHandleAudioBecomingNoisy`).
- [x] Si se pausó por eso, vuelve a sonar sola al conectar otra vez unos auriculares, un dispositivo
  Bluetooth de audio o el coche (con 1,5 s de margen para que Android cambie la salida). Si el usuario
  pausa o reproduce a mano entretanto, no se reanuda sola. Las conexiones solo para llamadas (SCO) no
  cuentan.
- [x] Los botones siguiente/anterior de auriculares Bluetooth y del coche llegan a la cola de la app.
  Antes ExoPlayer los atendía por su cuenta: no hacían nada o saltaban a la canción precargada sin que
  la app se enterase.
- [x] Las llamadas ya pausaban y reanudaban por el foco de audio; ahora el audio se declara como música.
- [ ] Sin probar en el teléfono. Si Android cierra la app mientras está en pausa mucho tiempo, no
  podrá reanudar al conectar (la exención de batería ayuda).
