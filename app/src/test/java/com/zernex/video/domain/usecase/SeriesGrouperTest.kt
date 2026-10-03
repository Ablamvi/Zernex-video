package com.zernex.video.domain.usecase

import android.net.Uri
import com.zernex.video.domain.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesGrouperTest {
    @Test fun detectsLongTitleAndKeepsItsCharacters() {
        val info = SeriesGrouper.parse("L'Attaque des Titans - La dernière bataille (version spéciale) S02E07 1080p.mkv")!!
        assertEquals("L'Attaque des Titans - La dernière bataille (version spéciale)", info.title)
        assertEquals(2, info.season)
        assertEquals(7, info.episode)
    }

    @Test fun supportsMultipleEpisodeNotations() {
        assertEquals(7, SeriesGrouper.parse("Ma Série ! S2E7.mkv")!!.episode)
        assertEquals(12, SeriesGrouper.parse("Ma Série (2026) 2x12 - Finale.mkv")!!.episode)
        assertEquals(4, SeriesGrouper.parse("Ma Série — Season 3 Episode 4.mkv")!!.episode)
        assertEquals(9, SeriesGrouper.parse("Ma Série [Ep 09].mkv")!!.episode)
        assertEquals(6, SeriesGrouper.parse("The Walking Dead Dead City saison 1 épisode 6 Streaming.mkv")!!.episode)
        assertEquals(1, SeriesGrouper.parse("The Walking Dead Dead City saison 1 épisode 6 Streaming.mkv")!!.season)
    }

    @Test fun keepsLongNamesWithNumbersInsideTitle() {
        val info = SeriesGrouper.parse("24 Heures Chrono - Saison 2024 - Jack Returns S01E03.mkv")!!
        assertEquals("24 Heures Chrono - Saison 2024 - Jack Returns", info.title)
        assertEquals(1, info.season)
        assertEquals(3, info.episode)
    }

    @Test fun groupsBySeriesAndSeasonAndSortsNumerically() {
        val videos = listOf(
            video("The.Show.S01E10.mp4"),
            video("The.Show.S01E2.mp4"),
            video("The.Show.S01E01.mp4"),
            video("Another.S02E01.mp4"),
            video("ordinary-video.mp4")
        )
        val groups = SeriesGrouper.group(videos)
        assertEquals(2, groups.size)
        val theShow = groups.first { it.first.title == "The Show" }
        assertEquals(3, theShow.second.size)
        assertEquals("The.Show.S01E01.mp4", theShow.second[0].name)
        assertEquals("The.Show.S01E2.mp4", theShow.second[1].name)
        assertEquals("The.Show.S01E10.mp4", theShow.second[2].name)
        assertTrue(groups.none { it.second.any { video -> video.name == "ordinary-video.mp4" } })
    }

    @Test fun separatesSeasons() {
        val groups = SeriesGrouper.group(listOf(
            video("The.Show.S01E01.mp4"),
            video("The.Show.S02E01.mp4")
        ))
        assertEquals(2, groups.size)
        assertTrue(groups.any { it.first.title == "The Show" && it.first.season == 1 })
        assertTrue(groups.any { it.first.title == "The Show" && it.first.season == 2 })
    }

    @Test fun groupsLongFrenchTitlesAcrossSeasonsAsOneSeries() {
        val groups = SeriesGrouper.groupBySeries(listOf(
            video("The Walking Dead Dead City saison 1 épisode 6 Streaming.mp4"),
            video("The Walking Dead Dead City saison 1 épisode 5 Streaming.mp4"),
            video("The Walking Dead Dead City saison 2 épisode 1 Streaming.mp4")
        ))
        assertEquals(1, groups.size)
        assertEquals("The Walking Dead Dead City", groups.first().title)
        assertEquals(2, groups.first().seasonCount)
        assertEquals(3, groups.first().episodeCount)
        assertEquals(5, SeriesGrouper.parse("The Walking Dead Dead City saison 1 épisode 5 Streaming.mp4")!!.episode)
        assertEquals(6, groups.first().seasons.first().episodes[1].let { SeriesGrouper.parse(it.name)!!.episode })
    }

    @Test fun doesNotTreatRandomNumbersAsEpisodes() {
        assertNull(SeriesGrouper.parse("Film 2026 1080p.mkv"))
        assertNull(SeriesGrouper.parse("Vacances au 20ème siècle.mp4"))
    }

    private fun video(name: String) = VideoItem(name.hashCode().toLong(), Uri.parse("content://zernex/test/${name.hashCode()}"), name, "video/mp4", "Movies/", 0, 0, 0, 0, 0, 0, 0f, 0)
}
