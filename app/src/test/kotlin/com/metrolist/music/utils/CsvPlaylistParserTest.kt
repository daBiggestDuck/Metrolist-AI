package com.metrolist.music.utils

import com.metrolist.music.viewmodels.CsvImportState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end parse test using a realistic Exportify CSV header row and sample tracks.
 */
class CsvPlaylistParserTest {
    private val exportifyHeader =
        "Track URI,Track Name,Album Name,Artist Name(s),Release Date,Duration (ms),Popularity,Explicit,Added By,Added At,Genres,Record Label,Danceability,Energy,Key,Loudness,Mode,Speechiness,Acousticness,Instrumentalness,Liveness,Valence,Tempo,Time Signature"

    private val exportifyRows =
        listOf(
            exportifyHeader,
            "spotify:track:abc1,Blinding Lights,After Hours,\"The Weeknd, Daft Punk\",2020-03-20,200040,85,false,user,2024-01-01,synthpop,Republic,0.5,0.7,1,-5,1,0.05,0.1,0,0.1,0.5,171,4",
            "spotify:track:abc2,Levitating,\"Future Nostalgia\",\"Dua Lipa\",2020-03-27,203064,90,false,user,2024-01-02,pop,Warner,0.7,0.8,6,-4,1,0.04,0.2,0,0.2,0.8,103,4",
        )

    @Test
    fun `parses exportify net CSV with quoted artist lists`() {
        val previewRows = exportifyRows.take(3).map { CsvParser.parseLine(it) }
        val mapping = CsvImportColumnDetector.detect(previewRows, hasHeader = true)

        assertEquals(1, mapping.titleColumnIndex)
        assertEquals(3, mapping.artistColumnIndex)
        assertTrue(mapping.hasHeader)

        val result = CsvPlaylistParser.parse(exportifyRows, mapping)

        assertEquals(2, result.tracks.size)
        assertEquals("Blinding Lights" to "The Weeknd, Daft Punk", result.tracks[0])
        assertEquals("Levitating" to "Dua Lipa", result.tracks[1])
        assertEquals(2, result.songs.size)
        assertEquals("Blinding Lights", result.songs[0].song.title)
    }

    @Test
    fun `parses exportify with UTF-8 BOM on header`() {
        val bomHeader = "\uFEFF$exportifyHeader"
        val lines = listOf(bomHeader) + exportifyRows.drop(1)
        val previewRows = lines.take(2).map { CsvParser.parseLine(it) }
        val mapping = CsvImportColumnDetector.detect(previewRows, hasHeader = true)
        val result = CsvPlaylistParser.parse(lines, mapping)

        assertEquals(2, result.tracks.size)
        assertEquals("Blinding Lights", result.tracks[0].first)
    }
}
