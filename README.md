# ZERNEX Video 2.3.0 — finition bibliothèque, séries et lecteur

## Périmètre
- Interface principale réduite à deux espaces : **Bibliothèque** et **Séries**.
- Page de lecture conservée visuellement et fonctionnellement, avec correction du double-appui latéral.
- Pendant la lecture, l’écran reste allumé ; la veille redevient normale lorsque la vidéo est en pause ou lorsque le lecteur est quitté.
- Suppression des écrans/fonctions secondaires : coffre-fort, boîte à outils, partage réseau, playlists et réglages dédiés.
- Android 8+, ARMv7 `armeabi-v7a`.

## Lecteur
- Double-appui à gauche : recul de 10 secondes.
- Double-appui à droite : avance de 10 secondes.
- Double-appui au centre : lecture/pause.
- Appui long latéral 2× : vitesse temporaire conservée.
- Sous-titres, vitesse, zoom, rotation, minuterie et PiP conservés dans le menu du lecteur.

## Validation
La compilation Android complète n’est pas disponible dans ce conteneur faute de SDK/Gradle local. Le workflow GitHub Actions du projet reste configuré pour construire, signer et vérifier l’APK Release.
