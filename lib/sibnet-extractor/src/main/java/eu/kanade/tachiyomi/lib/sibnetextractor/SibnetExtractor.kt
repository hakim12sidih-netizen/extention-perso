package eu.kanade.tachiyomi.lib.sibnetextractor

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient

class SibnetExtractor(private val client: OkHttpClient, private val headers: Headers) {
    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    fun videosFromUrl(url: String, headers: Headers): List<Video> = videosFromUrl(url, "Sibnet")

    fun videosFromUrl(url: String, prefix: String = ""): List<Video> {
        val document = client.newCall(GET(url, headers)).execute().asJsoup()
        val playerScript = document.selectFirst("script:containsData(player.src)")?.data()

        if (playerScript != null) {
            val playlistUrl = playerScript.substringAfter("file:\"").substringBefore("\"")
            return playlistUtils.extractFromHls(
                playlistUrl,
                referer = url,
                videoNameGen = { quality -> "Sibnet: $quality".let { if (prefix.isNotBlank()) "$prefix $it" else it } },
            )
        }

        return document.select("script:containsData(file:)")
            .map { it.data() }
            .filter { it.contains("file:\"") }
            .map {
                val quality = it.substringAfter("height:").substringBefore(",").trim() + "p"
                val videoUrl = it.substringAfter("file:\"").substringBefore("\"")
                Video(videoUrl, "Sibnet: $quality".let { if (prefix.isNotBlank()) "$prefix $it" else it }, videoUrl)
            }
    }
}
