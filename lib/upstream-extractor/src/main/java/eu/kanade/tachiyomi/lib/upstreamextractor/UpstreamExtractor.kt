package eu.kanade.tachiyomi.lib.upstreamextractor

import dev.datlag.jsunpacker.JsUnpacker
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient

class UpstreamExtractor(private val client: OkHttpClient, private val headers: Headers) {
    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    fun videosFromUrl(url: String, headers: Headers): List<Video> = videosFromUrl(url, "Upstream")

    fun videosFromUrl(url: String, prefix: String = ""): List<Video> =
        runCatching {
            val jsE = client.newCall(GET(url, headers)).execute().asJsoup().selectFirst("script:containsData(eval)")!!.data()
            val masterUrl = JsUnpacker.unpackAndCombine(jsE)!!.substringAfter("{file:\"").substringBefore("\"}")
            playlistUtils.extractFromHls(masterUrl, videoNameGen = { "Upstream - $it".let { if (prefix.isNotBlank()) "$prefix $it" else it } })
        }.getOrDefault(emptyList())
}
