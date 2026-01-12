package eu.kanade.tachiyomi.lib.doodextractor

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.OkHttpClient

class DoodExtractor(private val client: OkHttpClient, private val headers: Headers) {

    fun videosFromUrl(url: String, headers: Headers, redirect: Boolean = true): List<Video> = videosFromUrl(url, "DoodStream", redirect)

    fun videosFromUrl(url: String, prefix: String = "", redirect: Boolean = true): List<Video> {
        return runCatching {
            val newUrl = if (redirect) {
                client.newCall(GET(url, this.headers)).execute().request.url.toString()
            } else {
                url
            }

            val content = client.newCall(GET(newUrl, this.headers)).execute().body.string()
            val pass_md5 = content.substringAfter("'/pass_md5/").substringBefore("',")
            val randomString = getRandomString()
            val expiry = System.currentTimeMillis()
            val doodHeaders = headers.newBuilder().add("Referer", "https://dood.wf/").build()
            val masterUrl = client.newCall(GET("https://dood.wf/pass_md5/$pass_md5", doodHeaders)).execute().body.string()
            val videoUrl = "$masterUrl$randomString?token=${pass_md5.substringAfterLast("/")}&expiry=$expiry"
            listOf(Video(videoUrl, prefix, videoUrl, headers = doodHeaders))
        }.getOrElse { emptyList() }
    }

    private fun getRandomString(length: Int = 10): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }
}
