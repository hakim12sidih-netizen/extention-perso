package eu.kanade.tachiyomi.lib.mixdropextractor

import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.unpacker.Unpacker
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.net.URLDecoder

class MixDropExtractor(private val client: OkHttpClient, private val headers: Headers) {

    fun videosFromUrl(url: String, headers: Headers): List<Video> = videosFromUrl(url, "MixDrop")

    fun videosFromUrl(url: String, prefix: String = ""): List<Video> {
        return runCatching {
            val document = client.newCall(GET(url, this.headers)).execute().asJsoup()
            val script = document.selectFirst("script:containsData(eval)")?.data()
                ?: return emptyList()

            val packed = script.substringAfter("eval(function(p,a,c,k,e,d)")
                .substringBefore("</script>")
            val unpacked = JsUnpacker("eval(function(p,a,c,k,e,d)$packed").unpack()
                ?: return emptyList()

            val videoUrl = "https:" + unpacked.substringAfter("Wsad='").substringBefore("'")
            val quality = "${prefix}MixDrop"
            listOf(Video(videoUrl, quality, videoUrl, headers = this.headers))
        }.getOrElse { emptyList() }
    }

    // Created by https://github.com/jmir1
    // Based on https://github.com/jmir1/aniyomi-extensions/blob/master/src/en/gogoanime/src/eu/kanade/tachiyomi/animeextension/en/gogoanime/JsUnpacker.kt
    private class JsUnpacker(private val packedJS: String) {
        fun unpack(): String? {
            try {
                val p = packedJS.substringAfter("eval(function(p,a,c,k,e,d){")
                    .substringBefore("}))")
                var h = p.substringBefore(".split('|'),0,{}))")
                val k = h.substringAfter("'.split('|'),0,{}))")
                    .substringAfterLast("'")
                    .split("|")
                h = h.substringBefore(k[0])
                val a = h.substringAfter("p,a,c,").substringBefore(",").toInt()
                val c = h.substringAfter("$a,").substringBefore(",").toInt()

                return JsDecoder(h, a, c, k).decode()
            } catch (e: Exception) {
                return null
            }
        }
    }

    private class JsDecoder(
        private var h: String,
        private val a: Int,
        private val c: Int,
        private val k: List<String>,
    ) {
        fun decode(): String? {
            h = h.substring(0, h.lastIndexOf("',") + 1)
            h = h.substring(h.indexOf("'") + 1, h.lastIndexOf("'"))

            var a = this.a
            var c = this.c
            val k = this.k.toMutableList()

            val base = object {
                val a = this@JsDecoder.a
                fun call(c: Int): String {
                    return if (c < a) "" else call(c / a) + if (c % a > 35) (c % a + 29).toChar() else (c % a).toString(36)
                }
            }

            while (c-- > 0) {
                if (k.size > c && k[c].isNotEmpty()) {
                    val regex = Regex("\\b${base.call(c)}\\b")
                    h = h.replace(regex, k[c])
                }
            }
            return h
        }
    }
}
