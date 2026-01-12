package eu.kanade.tachiyomi.lib.vudeoextractor

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient

class VudeoExtractor(private val client: OkHttpClient, private val headers: Headers) {

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    fun videosFromUrl(url: String, headers: Headers): List<Video> = videosFromUrl(url, "Vudeo")

    fun videosFromUrl(url: String, prefix: String = ""): List<Video> {
        val document = client.newCall(GET(url, headers)).execute().asJsoup()
        val masterUrl = document.selectFirst("source#video_source")?.attr("src")
            ?: return emptyList()

        return playlistUtils.extractFromHls(
            masterUrl,
            referer = url,
            videoNameGen = { quality -> "Vudeo: $quality".let { if (prefix.isNotBlank()) "$prefix $it" else it } },
        )
    }
}
