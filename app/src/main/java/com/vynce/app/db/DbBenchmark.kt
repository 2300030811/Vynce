package com.vynce.app.db

import android.content.Context
import android.util.Log
import com.vynce.app.db.entities.ArtistEntity
import com.vynce.app.db.entities.SongArtistMap
import com.vynce.app.db.entities.SongEntity
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.LocalDateTime
import kotlin.system.measureTimeMillis
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DbBenchmark {
    private const val TAG = "DbBenchmark"

    suspend fun runDatabaseBenchmarks(context: Context) = withContext(Dispatchers.IO) {
        val resultLog = StringBuilder()
        fun log(msg: String) {
            Log.i(TAG, msg)
            resultLog.appendLine(msg)
        }

        log("--- Starting Database Benchmark ---")

        // 1. Setup Test Database
        context.deleteDatabase("benchmark.db")
        val db = InternalDatabase.newTestInstance(context, "benchmark.db")

        // 2. Seed 50,000 songs, 5,000 artists
        log("Seeding Database...")
        val seedTime = measureTimeMillis {
            val artists = (1..5000).map { i ->
                ArtistEntity(
                    id = "ARTIST_$i",
                    name = "Artist Name $i",
                    isLocal = true
                )
            }
            val songs = (1..50000).map { i ->
                SongEntity(
                    id = "SONG_$i",
                    title = "Song Title $i",
                    inLibrary = LocalDateTime.now(),
                    isLocal = true,
                    localPath = "/storage/emulated/0/Music/Song_$i.mp3"
                )
            }
            val songArtistMaps = (1..50000).map { i ->
                SongArtistMap(
                    songId = "SONG_$i",
                    artistId = "ARTIST_${Random.nextInt(1, 5001)}",
                    position = 0
                )
            }

            db.transaction {
                artists.forEach { db.insert(it) }
                songs.forEach { db.insert(it) }
                songArtistMaps.forEach { db.insert(it) }
            }
        }
        log("Seeding completed in ${seedTime}ms")

        // 3. EXPLAIN QUERY PLAN
        log("\n--- EXPLAIN QUERY PLANS ---")
        val queriesToExplain = listOf(
            "songsByArtistAsc" to "SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY (SELECT LOWER(GROUP_CONCAT(name, '')) FROM artist WHERE id IN (SELECT artistId FROM song_artist_map WHERE songId = song.id) ORDER BY name) COLLATE NOCASE",
            "songsByPlayCountAsc" to "SELECT song.*, (SELECT SUM(playCount.count) FROM playCount WHERE playCount.song = song.id) AS pc FROM song WHERE inLibrary IS NOT NULL ORDER BY pc ASC",
            "mostPlayedSongs" to "SELECT song.*, IFNULL(SUM(playCount.count), 0) as playCount FROM song LEFT JOIN playCount ON playCount.song = song.id WHERE song.inLibrary IS NOT NULL GROUP BY song.id ORDER BY playCount DESC LIMIT 10"
        )
        
        queriesToExplain.forEach { (name, sql) ->
            val cursor = db.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN $sql")
            log("Plan for $name:")
            while (cursor.moveToNext()) {
                log("  - ${cursor.getString(3)}") // Detail column
            }
            cursor.close()
        }

        // 4. Benchmark songsByArtistAsc (The primary bottleneck)
        log("\n--- BENCHMARK: songsByArtistAsc() ---")
        try {
            val sortTime = measureTimeMillis {
                val list = db.songsByArtistAsc().first()
                log("Sorted ${list.size} songs")
            }
            log("Query completed in ${sortTime}ms")
        } catch (e: Exception) {
            log("Query failed: ${e.message}")
        }

        // 5. Benchmark LocalMediaScanner Artist Matching Algorithms
        log("\n--- BENCHMARK: Artist Matching (5000 queries) ---")
        val searchNames = (1..5000).map { "Artist Name ${Random.nextInt(1, 10000)}" }
        
        // Approach A: Current LIKE query (Hits DB every time)
        val sqlTime = measureTimeMillis {
            var hits = 0
            searchNames.forEach { name ->
                val result = db.localArtistsByNameFuzzy(name)
                if (result.isNotEmpty()) hits++
            }
            log("SQL LIKE matched $hits artists")
        }
        log("Approach A (SQL LIKE): ${sqlTime}ms")

        // Approach B: In-Memory Contains
        val inMemoryArtists = db.allLocalArtists()
        val containsTime = measureTimeMillis {
            var hits = 0
            searchNames.forEach { name ->
                val lowerName = name.lowercase()
                val match = inMemoryArtists.find { it.name.lowercase().contains(lowerName) }
                if (match != null) hits++
            }
        }
        log("Approach B (In-Memory Contains): ${containsTime}ms")

        // Approach C: In-Memory HashMap Lookup
        val hashTime = measureTimeMillis {
            var hits = 0
            val artistMap = inMemoryArtists.associateBy { it.name.lowercase() }
            searchNames.forEach { name ->
                val match = artistMap[name.lowercase()]
                if (match != null) hits++
            }
        }
        log("Approach C (HashMap exact match): ${hashTime}ms")

        // 6. Write results to Desktop/Download dir if possible, or context files dir
        val outputFile = File(context.getExternalFilesDir(null), "DbBenchmarkResults.txt")
        outputFile.writeText(resultLog.toString())
        log("\nBenchmark complete. Results saved to: ${outputFile.absolutePath}")
    }
}
