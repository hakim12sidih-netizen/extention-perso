package extensionsfr.utils

import android.app.Application
import android.content.SharedPreferences
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.collections.mapOf

abstract class AnimeDomainSource: AnimeHttpSource(), ConfigurableAnimeSource {

    protected open val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    protected open val prefDomainKey = "preferred_domain"
    protected open val prefDomainTitle = "Server address"
    protected open val prefDomainDefault = ""

    override val baseUrl by lazy {
        preferences.getString(prefDomainKey, prefDomainDefault)!!
    }

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        checkBaseUrl()
        return super.getPopularAnime(page)
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        checkBaseUrl()
        return super.getLatestUpdates(page)
    }

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        checkBaseUrl()
        return super.getSearchAnime(page, query, filters)
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        checkBaseUrl()
        return super.getAnimeDetails(anime)
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        checkBaseUrl()
        return super.getEpisodeList(anime)
    }

    protected fun checkBaseUrl() = require(baseUrl.isNotBlank()) { MISSING_DOMAIN_MESSAGE.locale() }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        Log.d(name, "$name $id ($versionId)")
        EditTextPreference(screen.context).apply {
            key = prefDomainKey
            title = prefDomainTitle
            summary = baseUrl.ifBlank { SUMMARY.locale() }
            setDefaultValue(prefDomainDefault)
            dialogTitle = title
            dialogMessage = DIALOG.locale()

            val validate = { str: String ->
                str.isBlank() || !str.endsWith("/") && str.toHttpUrlOrNull().let { url ->
                    url != null && url.pathSize == 1 && url.pathSegments[0].isBlank()
                }
            }

            setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                editText.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

                    override fun afterTextChanged(editable: Editable?) {
                        requireNotNull(editable)
                        val text = editable.toString()
                        val valid = validate(text)
                        editText.error = if (!valid) BASE_URL_ERROR.locale() else null
                        editText.rootView.findViewById<Button>(android.R.id.button1)?.isEnabled = editText.error == null
                    }
                })
            }

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val text = newValue as String
                    val valid = validate(text)
                    if (valid) {
                        Toast.makeText(
                            screen.context,
                            RESTART_ANIYOMI.locale(),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    valid
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }.also(screen::addPreference)
    }

    private fun Map<String, String>.locale() = get(lang) ?: get("en")!!

    companion object {
        val PREF_DOMAIN_TITLE = mapOf(
            "en" to "Server address",
            "fr" to "Adresse du serveur",
        )

        val SUMMARY = mapOf(
            "en" to "None",
            "fr" to "Aucune",
        )

        val DIALOG = mapOf(
            "en" to "Enter the server address",
            "fr" to "Entrez l'adresse du serveur",
        )

        val BASE_URL_ERROR = mapOf(
            "en" to "The address must not end with a forward slash (/).",
            "fr" to "L'adresse ne doit pas se terminer par un slash (/).",
        )

        val RESTART_ANIYOMI = mapOf(
            "en" to "Restart Aniyomi to apply new setting.",
            "fr" to "Relancez Aniyomi pour appliquer les nouveaux paramètres."
        )

        val MISSING_DOMAIN_MESSAGE = mapOf(
            "en" to "\n\nConfigure server address in extension settings.\n\nMore options (⋮ top right) > Settings > ${PREF_DOMAIN_TITLE.get("en")}",
            "fr" to "\n\nConfigurez l'adresse du serveur dans les paramètres de l'extension.\n\nPlus d'options (⋮ en haut à droite) > Paramètres > ${PREF_DOMAIN_TITLE.get("fr")}"
        )
    }

}