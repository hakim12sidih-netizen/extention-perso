package eu.kanade.tachiyomi.animeextension.all.sudatchi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EpisodeDto(
    val title: String,
    val id: Int,
    val number: Int,
    val isProcessed: Boolean,
)

@Serializable
data class EpisodeDataDto(
    val episode: EpisodeDto,
    val subtitlesJson: String,
)

@Serializable
data class SubtitleLangDto(
    val name: String,
    val language: String,
)

@Serializable
data class SubtitleDto(
    val url: String,
    @SerialName("SubtitlesName")
    val subtitlesName: SubtitleLangDto,
)

@Serializable
data class CoverImageDto(
    val extraLarge: String,
)

@Serializable
data class SeriesDto(
    val id: Int,
    val title: AnimeTitleDto,
    val coverImage: CoverImageDto,
)

@Serializable
data class GetSeriesDto(
    val series: List<SeriesDto>,
)

@Serializable
data class AnimeSpotlightDto(
    val id: Int,
    val title: String,
    val coverImage: String,
)

@Serializable
data class LatestEpisodeAnimeDto(
    val anilistId: Int,
)

@Serializable
data class LatestEpisodeDto(
    @SerialName("Anime")
    val anime: LatestEpisodeAnimeDto,
)

@Serializable
data class HomeDataDto(
    @SerialName("AnimeSpotlight")
    val animeSpotlight: List<AnimeSpotlightDto>,
    val latestEpisodes: List<LatestEpisodeDto>,
)

@Serializable
data class AnimeTitleDto(
    val english: String?,
    val native: String?,
    val romaji: String?,
) {
    val titles by lazy { arrayOf(english, native, romaji) }
}

@Serializable
data class AnimeDto(
    val id: Int,
    val title: AnimeTitleDto,
    val description: String,
    val status: String,
    val coverImage: String,
    val genres: List<String>,
    val studio: String,
    val producers: List<String>,
    val episodes: List<EpisodeDto>,
)
