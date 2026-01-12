package eu.kanade.tachiyomi.lib.streamvidextractor

import dev.datlag.jsunpacker.JsUnpacker
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient

class StreamVidExtractor(private val client: OkHttpClient, private val headers: Headers) {
    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    fun videosFromUrl(url: String, headers: Headers, sourceChange: Boolean = false): List<Video> = videosFromUrl(url, "StreamVid", sourceChange)

    fun videosFromUrl(url: String, prefix: String = "", sourceChange: Boolean = false): List<Video> {
        return runCatching {
            val doc = client.newCall(GET(url, headers)).execute().asJsoup()

            val script = doc.selectFirst("script:containsData(eval):containsData(p,a,c,k,e,d)")?.data()
                ?.let(JsUnpacker::unpackAndCombine)
                ?: return emptyList()
            val masterUrl = if (!sourceChange) {
                script.substringAfter("sources:[{src:\"").substringBefore("\",")
            } else {
                script.substringAfter("sources:[{file:\"").substringBefore("\"")
            }
            playlistUtils.extractFromHls(masterUrl, videoNameGen = { "${prefix}StreamVid - (${it}p)" })
        }.getOrElse { emptyList() }
    }
}
