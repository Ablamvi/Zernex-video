package com.zernex.video.domain.usecase

import com.zernex.video.domain.model.Series
import com.zernex.video.domain.model.SeriesGroup
import com.zernex.video.domain.model.SeriesSeason
import com.zernex.video.domain.model.VideoItem
import java.text.Normalizer

/**
 * Smart episode detection for real-world filenames.
 * The important signal is the episode marker, not the shape/length of the title.
 */
object SeriesGrouper {
    data class EpisodeInfo(
        val title: String,
        val season: Int,
        val episode: Int
    )

    private val sxxexx = Regex(
        "(?i)(?<![\\p{L}\\p{N}])S\\s*(\\d{1,3})\\s*[._ -]?\\s*E\\s*(\\d{1,4})(?![\\p{L}\\p{N}])"
    )
    private val nx = Regex(
        "(?i)(?<![\\p{L}\\p{N}])(\\d{1,3})\\s*[x×]\\s*(\\d{1,4})(?![\\p{L}\\p{N}])"
    )
    private val seasonEpisode = Regex(
        "(?i)(?<![\\p{L}\\p{N}])(?:Season|Saison)\\s*(\\d{1,3})\\s*(?:[-._ ]+)?(?:Episode|Ep|[Éé]pisode|[Éé]p)\\s*(\\d{1,4})(?![\\p{L}\\p{N}])"
    )
    private val shortSeasonEpisode = Regex(
        "(?i)(?<![\\p{L}\\p{N}])S\\s*(\\d{1,3})\\s*(?:[-._ ]+)?(?:Episode|Ep|[Éé]pisode|[Éé]p)\\s*(\\d{1,4})(?![\\p{L}\\p{N}])"
    )
    private val reverseSeasonEpisode = Regex(
        "(?i)(?<![\\p{L}\\p{N}])(?:Episode|Ep|[Éé]pisode|[Éé]p)\\s*(\\d{1,4})\\s*(?:[-._ ]+)?(?:de la |of the )?(?:Season|Saison)\\s*(\\d{1,3})(?![\\p{L}\\p{N}])"
    )
    private val episodeOnly = Regex(
        "(?i)(?<![\\p{L}\\p{N}])(?:Episode|Ep|[Éé]pisode|[Éé]p)\\s*(\\d{1,4})(?![\\p{L}\\p{N}])"
    )
    private val seasonOnly = Regex(
        "(?i)(?<![\\p{L}\\p{N}])(?:Season|Saison)\\s*(\\d{1,3})(?![\\p{L}\\p{N}])"
    )

    fun parse(name: String): EpisodeInfo? {
        val base = name.substringBeforeLast('.', name).trim()

        sxxexx.find(base)?.let { match ->
            return buildInfo(base, match.range.first, match.groupValues[1], match.groupValues[2])
        }
        nx.find(base)?.let { match ->
            return buildInfo(base, match.range.first, match.groupValues[1], match.groupValues[2])
        }
        seasonEpisode.find(base)?.let { match ->
            return buildInfo(base, match.range.first, match.groupValues[1], match.groupValues[2])
        }
        shortSeasonEpisode.find(base)?.let { match ->
            return buildInfo(base, match.range.first, match.groupValues[1], match.groupValues[2])
        }
        reverseSeasonEpisode.find(base)?.let { match ->
            return buildInfo(base, match.range.first, match.groupValues[2], match.groupValues[1])
        }

        val episode = episodeOnly.find(base) ?: return null
        val season = seasonOnly.find(base.substring(0, episode.range.first))?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        val titleEnd = seasonOnly.find(base.substring(0, episode.range.first))?.range?.first ?: episode.range.first
        val title = cleanTitle(base.substring(0, titleEnd))
        val episodeNumber = episode.groupValues[1].toIntOrNull() ?: return null
        if (title.isBlank() || season < 1 || episodeNumber < 1) return null
        return EpisodeInfo(title, season, episodeNumber)
    }

    private fun buildInfo(base: String, start: Int, seasonRaw: String, episodeRaw: String): EpisodeInfo? {
        val season = seasonRaw.toIntOrNull() ?: return null
        val episode = episodeRaw.toIntOrNull() ?: return null
        if (season < 1 || episode < 1) return null
        val title = cleanTitle(base.substring(0, start))
        if (title.isBlank()) return null
        return EpisodeInfo(title, season, episode)
    }

    /** Keeps the old season-level API for compatibility/tests. */
    fun group(videos: List<VideoItem>): List<Pair<Series, List<VideoItem>>> =
        groupBySeries(videos).flatMap { group ->
            group.seasons.map { season ->
                Pair(
                    Series("${group.id}#${season.season}", group.title, season.season, season.episodes.size),
                    season.episodes
                )
            }
        }

    /** Groups all seasons of the same show into one intelligent series entity. */
    fun groupBySeries(videos: List<VideoItem>): List<SeriesGroup> {
        data class Parsed(val info: EpisodeInfo, val video: VideoItem)

        val parsed = videos.mapNotNull { video -> parse(video.name)?.let { Parsed(it, video) } }
        val grouped = linkedMapOf<String, MutableList<Parsed>>()
        parsed.forEach { item ->
            grouped.getOrPut(canonical(item.info.title)) { mutableListOf() }.add(item)
        }

        return grouped.map { (key, items) ->
            val title = chooseBestTitle(items.map { it.info.title })
            val seasons = items
                .groupBy { it.info.season }
                .toSortedMap()
                .map { (seasonNumber, seasonItems) ->
                    SeriesSeason(
                        seasonNumber,
                        seasonItems.sortedWith(
                            compareBy<Parsed> { it.info.episode }.thenBy { it.video.name.lowercase() }
                        ).map { it.video }
                    )
                }
            SeriesGroup("series:${key.hashCode()}", title, seasons)
        }.sortedBy { canonical(it.title) }
    }

    private fun chooseBestTitle(titles: List<String>): String =
        titles.maxByOrNull { scoreTitle(it) } ?: "Série"

    private fun scoreTitle(title: String): Int =
        title.length + title.count { it == ' ' } * 2 - title.count { it == '_' || it == '.' }

    private fun cleanTitle(raw: String): String = raw
        .replace(Regex("(?i)[._-]+\\s*(?:streaming|vf|vostfr|vost|french|français|web[- ]?dl|webrip|bluray|bdrip|hdrip|x264|x265|hevc|h264|h265)\\s*$"), "")
        .trim()
        .trim('.', '_', '-', ' ', '·', '•', '[', '(', '{')
        .replace(Regex("[._]+"), " ")
        .replace(Regex("\\s{2,}"), " ")
        .trim()

    private fun canonical(title: String): String =
        Normalizer.normalize(title.lowercase(java.util.Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[._\\-–—:]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
