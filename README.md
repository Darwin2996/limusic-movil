# LiMusic para Android

Cliente de YouTube Music para Android, sin anuncios en el audio. Es un **port no oficial** de
[Limusic](https://github.com/SimoHypers/limusic), la app de escritorio de SimoHypers (Rust + Tauri),
adaptado a teléfonos. No es la versión oficial ni está respaldado por su autor.

## Descarga e instalación

1. Descarga la APK de la [última versión](https://github.com/Darwin2996/limusic-movil/releases/latest)
   desde el propio móvil.
2. Ábrela. Android pedirá permiso para instalar apps de esa fuente la primera vez.
3. Inicia sesión con tu cuenta de Google desde **Más ▸ Cuenta**. Se usa la página oficial de Google y
   LiMusic solo guarda la sesión, nunca la contraseña.

Las versiones nuevas se anuncian dentro de la app, que descarga la APK y abre el instalador de Android
(siempre pide confirmarlo).

Requisitos: Android 7 o posterior en un móvil de 64 bits (arm64).

## Qué incluye

- Tu biblioteca, playlists, «Me gusta» e historial de YouTube Music, sincronizados con tu cuenta.
- Reproductor a pantalla completa con cola, letras y comentarios, y una barra plegada con gestos
  (desliza para cambiar de canción).
- Reproducción en segundo plano con controles en la notificación y la pantalla de bloqueo.
- Descargas para escuchar sin conexión, de canciones sueltas o de álbumes y playlists enteros.
- Temporizador de apagado, calidad distinta con Wi-Fi y con datos móviles, y música local del teléfono.
- Escuchar juntos: la misma música a la vez con otras personas.
- Estadísticas de escucha, scrobbling a Last.fm y ListenBrainz, y temas de color.

Lo que ha cambiado respecto al original está en [CAMBIOS_MOVIL.md](CAMBIOS_MOVIL.md), y lo pendiente
en [TAREAS_MOVIL.md](TAREAS_MOVIL.md).

## Compilar y publicar

El proyecto es el de Limusic con su parte Android en `src-tauri/gen/android`. Para generar la APK y
publicarla como release:

```powershell
powershell -File scripts/android-release.ps1 -Version X.Y.Z -Notes "Qué ha cambiado"
```

El script describe lo que necesita (Android SDK y NDK, JDK 21, Rust con el target
`aarch64-linux-android`, pnpm y `gh`). Las actualizaciones solo se instalan sobre una app firmada con
la misma clave, así que todas las versiones tienen que compilarse con el mismo almacén de claves.

La documentación de la app de escritorio original sigue en
[docs/README-original.md](docs/README-original.md).

## Créditos

- [Limusic](https://github.com/SimoHypers/limusic), de SimoHypers: la app en la que se basa este port.
- [Metrolist](https://github.com/mostafaalagamy/Metrolist), el cliente Android de YouTube Music en cuyo
  motor de reproducción se inspiró Limusic.

## Aviso

Este proyecto no está afiliado, financiado, autorizado ni respaldado por YouTube, Google LLC ni
ninguna de sus filiales, ni tiene relación alguna con ellas. Todas las marcas y derechos de propiedad
intelectual mencionados pertenecen a sus respectivos dueños.

## Licencia

[GPL-3.0](LICENSE), la misma que el proyecto original.
