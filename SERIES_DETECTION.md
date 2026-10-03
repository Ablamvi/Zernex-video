# Détection intelligente des séries

ZERNEX détecte les épisodes à partir de marqueurs explicites dans les noms de fichiers.

Formats reconnus :
- `S01E01`, `S1E1`, `S 01 E 01`
- `1x01` et `1×01`
- `Season 1 Episode 1`
- `Season 1 Ep 1`
- `Episode 12` / `Ep 12` (saison 1 par défaut)

Le nom de la série est tout ce qui précède le marqueur d'épisode. Les caractères, accents,
nombres présents dans le titre et titres longs sont conservés. Les séparateurs techniques
(`.`, `_`) sont seulement normalisés pour l'affichage. Les simples années ou nombres ne sont
pas considérés comme des épisodes.
