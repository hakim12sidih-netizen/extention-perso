package eu.kanade.tachiyomi.lib.streamhubextractor

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.OkHttpClient

class StreamHubExtractor(private val client: OkHttpClient, private val headers: Headers) {
    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    fun videosFromUrl(url: String, headers: Headers): List<Video> = videosFromUrl(url, "StreamHub")

    fun videosFromUrl(url: String, prefix: String = ""): List<Video> {
        val document = client.newCall(GET(url, headers)).execute().body.string()
        val id = REGEX_ID.find(document)?.groupValues?.get(1)
        val sub = REGEX_SUB.find(document)?.groupValues?.get(1)
        val masterUrl = "https://$sub.streamhub.ink/hls/,$id,.urlset/master.m3u8"
        return playlistUtils.extractFromHls(masterUrl, videoNameGen = { "${prefix}StreamHub - ($it)" })
    }

    companion object {
        private val REGEX_ID = Regex("urlset\\|(.*?)\\|")
        private val REGEX_SUB = Regex("width\\|(.*?)\\|")
    }
}
