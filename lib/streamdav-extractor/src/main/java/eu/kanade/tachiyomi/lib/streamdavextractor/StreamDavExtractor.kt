package eu.kanade.tachiyomi.lib.streamdavextractor

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient

class StreamDavExtractor(private val client: OkHttpClient, private val headers: Headers) {
    fun videosFromUrl(url: String, headers: Headers): List<Video> = videosFromUrl(url, "StreamDav")

    fun videosFromUrl(url: String, prefix: String = ""): List<Video> {
        val document = client.newCall(GET(url, headers)).execute().asJsoup()
        return document.select("source").map {
            val videoUrl = it.attr("src")
            val quality = it.attr("label")
            Video(videoUrl, "${prefix}StreamDav - ($quality)", videoUrl)
        }
    }
}
