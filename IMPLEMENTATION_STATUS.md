# ZERNEX Video 2.3.0 — état de finition

Cette version est volontairement recentrée sur trois éléments utilisateur :
1. Bibliothèque locale
2. Séries détectées
3. Lecteur vidéo

La navigation inférieure ne contient plus que **Bibliothèque** et **Séries**. Les écrans Coffre-fort, Boîte à outils, Partage réseau, Playlists et Réglages ont été retirés de l’interface et leurs modules UI ont été supprimés.

Corrections du lecteur :
- double-appui gauche = recul 10 s ;
- double-appui droit = avance 10 s ;
- double-appui central = lecture/pause ;
- `FLAG_KEEP_SCREEN_ON` actif uniquement pendant la lecture ;
- nettoyage du flag lorsque le lecteur est quitté.
