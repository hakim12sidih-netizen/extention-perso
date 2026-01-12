package eu.kanade.tachiyomi.animeextension.all.sudatchi

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animeextension.all.sudatchi.dto.AnimeDto
import eu.kanade.tachiyomi.animeextension.all.sudatchi.dto.AnimeSpotlightDto
import eu.kanade.tachiyomi.animeextension.all.sudatchi.dto.EpisodeDataDto
import eu.kanade.tachiyomi.animeextension.all.sudatchi.dto.GetSeriesDto
import eu.kanade.tachiyomi.animeextension.all.sudatchi.dto.HomeDataDto
import eu.kanade.tachiyomi.animeextension.all.sudatchi.dto.SubtitleDto
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.parallelMapBlocking
import eu.kanade.tachiyomi.util.parseAs
import extensionsfr.utils.AnimeDomainSource
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class Sudatchi : AnimeDomainSource() {

    override val name = "Sudatchi"
    private val ipfsUrl by lazy { baseUrl.replaceFirst("://", "://ipfs.") }

    override val lang = "all"

    override val supportsLatest = true

    private val codeRegex by lazy { Regex("""\((.*)\)""") }

    private val json: Json by injectLazy()

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int) = GET("$baseUrl/api/fetchHomeData", headers)

    override fun popularAnimeParse(response: Response): AnimesPage {
        val data = response.parseAs<HomeDataDto>().animeSpotlight
        return AnimesPage(data.map { it.toSAnime() }.filterNot { it.status == SAnime.LICENSED }, false)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = popularAnimeRequest(page)

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val data = response.parseAs<HomeDataDto>().latestEpisodes.distinctBy { it.anime.anilistId }
        return AnimesPage(
            data.parallelMapBlocking {
                getAnimeDetails(SAnime.create().apply { url = "/anime/${it.anime.anilistId}" })
            },
            false,
        )
    }

    // =============================== Search ===============================
    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        return if (query.startsWith(PREFIX_SEARCH)) { // URL intent handler
            val id = query.removePrefix(PREFIX_SEARCH)
            client.newCall(GET("$baseUrl/api/anime/$id", headers))
                .awaitSuccess()
                .use(::searchAnimeByIdParse)
        } else {
            super.getSearchAnime(page, query, filters)
        }
    }

    private fun searchAnimeByIdParse(response: Response): AnimesPage {
        val details = animeDetailsParse(response).apply {
            setUrlWithoutDomain(response.request.url.toString())
            initialized = true
        }

        return AnimesPage(listOf(details), false)
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = "$baseUrl/api/getSeries".toHttpUrl().newBuilder()
        url.addQueryParameter("page", page.toString())
        url.addQueryParameter("perPage", "20")
        url.addQueryParameter("search", query)
        return GET(url.build(), headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val data = response.parseAs<GetSeriesDto>()
        val anime = data.series.map {
            SAnime.create().apply {
                title = it.title.english ?: it.title.romaji ?: it.title.native ?: "No Title"
                thumbnail_url = it.coverImage.extraLarge
                url = "/anime/${it.id}"
            }
        }
        return AnimesPage(anime, anime.isNotEmpty())
    }

    // =========================== Anime Details ============================
    override fun getAnimeUrl(anime: SAnime) = "$baseUrl${anime.url}"

    override fun animeDetailsRequest(anime: SAnime) = GET("$baseUrl/api${anime.url}", headers)

    override fun animeDetailsParse(response: Response): SAnime {
        val data = response.parseAs<AnimeDto>()
        return data.toSAnime(preferences.title)
    }

    // ============================== Episodes ==============================
    override fun episodeListRequest(anime: SAnime) = animeDetailsRequest(anime)

    override fun episodeListParse(response: Response): List<SEpisode> {
        val anime = response.parseAs<AnimeDto>()
        return anime.episodes.map {
            SEpisode.create().apply {
                name = it.title
                episode_number = it.number.toFloat()
                url = "/episode/${anime.id}/${it.number}"
            }
        }.reversed()
    }

    // ============================ Video Links =============================
    override fun videoListRequest(episode: SEpisode) = GET("$baseUrl/api${episode.url}", headers)

    private val playlistUtils: PlaylistUtils by lazy { PlaylistUtils(client, headers) }

    override fun videoListParse(response: Response): List<Video> {
        val data = response.parseAs<EpisodeDataDto>()
        val subtitles = json.decodeFromString<List<SubtitleDto>>(data.subtitlesJson)
        // val videoUrl = client.newCall(GET("$baseUrl/api/streams?episodeId=${data.episode.id}", headers)).execute().parseAs<StreamsDto>().url
        // keeping it in case the simpler solution breaks, can be hardcoded to this for now :
        val videoUrl = "$baseUrl/api/streams?episodeId=${data.episode.id}"
        return playlistUtils.extractFromHls(
            videoUrl,
            videoNameGen = { "Sudatchi (Private IPFS Gateway) - $it" },
            subtitleList = subtitles.map {
                Track("$ipfsUrl${it.url}", "${it.subtitlesName.name} (${it.subtitlesName.language})")
            }.sort(),
        )
    }

    @JvmName("trackSort")
    private fun List<Track>.sort(): List<Track> {
        val subtitles = preferences.subtitles
        return sortedWith(
            compareBy(
                { codeRegex.find(it.lang)!!.groupValues[1] != subtitles },
                { codeRegex.find(it.lang)!!.groupValues[1] != PREF_SUBTITLES_DEFAULT },
                { it.lang },
            ),
        )
    }

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.quality
        return sortedWith(
            compareBy { it.quality.contains(quality) },
        ).reversed()
    }

    // ============================ Preferences =============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        super.setupPreferenceScreen(screen)
        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = PREF_QUALITY_TITLE
            entries = PREF_QUALITY_ENTRIES.map { it.first }.toTypedArray()
            entryValues = PREF_QUALITY_ENTRIES.map { it.second }.toTypedArray()
            setDefaultValue(PREF_QUALITY_DEFAULT)
            summary = "%s"

            setOnPreferenceChangeListener { _, new ->
                val index = findIndexOfValue(new as String)
                preferences.edit().putString(key, entryValues[index] as String).commit()
            }
        }.also(screen::addPreference)
        ListPreference(screen.context).apply {
            key = PREF_SUBTITLES_KEY
            title = PREF_SUBTITLES_TITLE
            entries = PREF_SUBTITLES_ENTRIES.map { it.first }.toTypedArray()
            entryValues = PREF_SUBTITLES_ENTRIES.map { it.second }.toTypedArray()
            setDefaultValue(PREF_SUBTITLES_DEFAULT)
            summary = "%s"

            setOnPreferenceChangeListener { _, new ->
                val index = findIndexOfValue(new as String)
                preferences.edit().putString(key, entryValues[index] as String).commit()
            }
        }.also(screen::addPreference)
        ListPreference(screen.context).apply {
            key = PREF_TITLE_KEY
            title = PREF_TITLE_TITLE
            entries = PREF_TITLE_ENTRIES.map { it.first }.toTypedArray()
            entryValues = PREF_TITLE_ENTRIES.map { it.second }.toTypedArray()
            setDefaultValue(PREF_TITLE_DEFAULT)
            summary = "%s"

            setOnPreferenceChangeListener { _, new ->
                val index = findIndexOfValue(new as String)
                preferences.edit().putString(key, entryValues[index] as String).commit()
            }
        }.also(screen::addPreference)
    }

    // ============================= Utilities ==============================
    private val SharedPreferences.quality get() = getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)!!
    private val SharedPreferences.subtitles get() = getString(PREF_SUBTITLES_KEY, PREF_SUBTITLES_DEFAULT)!!
    private val SharedPreferences.title get() = getString(PREF_TITLE_KEY, PREF_TITLE_DEFAULT)!!

    private fun String.parseStatus() = when (this) {
        "NOT_YET_RELEASED" -> SAnime.LICENSED // Not Yet Released
        "RELEASING" -> SAnime.ONGOING
        "FINISHED" -> SAnime.COMPLETED
        else -> SAnime.UNKNOWN
    }

    private fun AnimeDto.toSAnime(titleLang: String) = SAnime.create().apply {
        url = "/anime/$id"
        title = when (titleLang) {
            "romaji" -> this@toSAnime.title.romaji
            "japanese" -> this@toSAnime.title.native
            else -> this@toSAnime.title.english
        } ?: (this@toSAnime.title.titles + "No Title").firstNotNullOf { it }
        description = this@toSAnime.description
        status = this@toSAnime.status.parseStatus()
        thumbnail_url = coverImage
        genre = genres.joinToString()
        artist = (producers + studio).distinct().joinToString()
    }

    private fun AnimeSpotlightDto.toSAnime() = SAnime.create().apply {
        url = "/anime/$id"
        title = this@toSAnime.title
        thumbnail_url = coverImage
    }

    companion object {
        const val PREFIX_SEARCH = "id:"

        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_TITLE = "Preferred quality"
        private const val PREF_QUALITY_DEFAULT = "1080"
        private val PREF_QUALITY_ENTRIES = arrayOf(
            Pair("1080p", "1080"),
            Pair("720p", "720"),
            Pair("480p", "480"),
        )

        private const val PREF_SUBTITLES_KEY = "preferred_subtitles"
        private const val PREF_SUBTITLES_TITLE = "Preferred subtitles"
        private const val PREF_SUBTITLES_DEFAULT = "eng"
        private val PREF_SUBTITLES_ENTRIES = arrayOf(
            Pair("Arabic (Saudi Arabia)", "ara"),
            Pair("Brazilian Portuguese", "por"),
            Pair("Chinese", "chi"),
            Pair("Croatian", "hrv"),
            Pair("Czech", "cze"),
            Pair("Danish", "dan"),
            Pair("Dutch", "dut"),
            Pair("English", "eng"),
            Pair("European Spanish", "spa-es"),
            Pair("Filipino", "fil"),
            Pair("Finnish", "fin"),
            Pair("French", "fra"),
            Pair("German", "deu"),
            Pair("Greek", "gre"),
            Pair("Hebrew", "heb"),
            Pair("Hindi", "hin"),
            Pair("Hungarian", "hun"),
            Pair("Indonesian", "ind"),
            Pair("Italian", "ita"),
            Pair("Japanese", "jpn"),
            Pair("Korean", "kor"),
            Pair("Latin American Spanish", "spa-419"),
            Pair("Malay", "may"),
            Pair("Norwegian Bokmål", "nob"),
            Pair("Polish", "pol"),
            Pair("Romanian", "rum"),
            Pair("Russian", "rus"),
            Pair("Swedish", "swe"),
            Pair("Thai", "tha"),
            Pair("Turkish", "tur"),
            Pair("Ukrainian", "ukr"),
            Pair("Vietnamese", "vie"),
        )

        private const val PREF_TITLE_KEY = "preferred_title"
        private const val PREF_TITLE_TITLE = "Preferred title"
        private const val PREF_TITLE_DEFAULT = "english"
        private val PREF_TITLE_ENTRIES = arrayOf(
            Pair("English", "english"),
            Pair("Romaji", "romaji"),
            Pair("Japanese", "japanese"),
        )
    }
}
