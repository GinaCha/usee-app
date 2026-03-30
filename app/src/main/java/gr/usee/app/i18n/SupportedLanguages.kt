package gr.usee.app.i18n

import java.util.Locale

data class AppLanguage(
    val code: String,
    val englishName: String,
    val nativeName: String,
    val flagEmoji: String
)

object SupportedLanguages {
    val all: List<AppLanguage> = listOf(
        AppLanguage(code = "en", englishName = "English",    nativeName = "English",    flagEmoji = "🇬🇧"),
        AppLanguage(code = "de", englishName = "German",     nativeName = "Deutsch",    flagEmoji = "🇩🇪"),
        AppLanguage(code = "fr", englishName = "French",     nativeName = "Français",   flagEmoji = "🇫🇷"),
        AppLanguage(code = "it", englishName = "Italian",    nativeName = "Italiano",   flagEmoji = "🇮🇹"),
        AppLanguage(code = "ru", englishName = "Russian",    nativeName = "Русский",    flagEmoji = "🇷🇺"),
        AppLanguage(code = "el", englishName = "Greek",      nativeName = "Ελληνικά",   flagEmoji = "🇬🇷"),
        AppLanguage(code = "uk", englishName = "Ukrainian",  nativeName = "Українська", flagEmoji = "🇺🇦"),
        AppLanguage(code = "es", englishName = "Spanish",    nativeName = "Español",    flagEmoji = "🇪🇸"),
        AppLanguage(code = "nl", englishName = "Dutch",      nativeName = "Nederlands", flagEmoji = "🇳🇱"),
        AppLanguage(code = "cs", englishName = "Czech",      nativeName = "Čeština",    flagEmoji = "🇨🇿"),
        AppLanguage(code = "pl", englishName = "Polish",     nativeName = "Polski",     flagEmoji = "🇵🇱"),
        AppLanguage(code = "bg", englishName = "Bulgarian",  nativeName = "Български",  flagEmoji = "🇧🇬"),
        AppLanguage(code = "ar", englishName = "Arabic",     nativeName = "العربية",    flagEmoji = "🇸🇦"),
        AppLanguage(code = "et", englishName = "Estonian",   nativeName = "Eesti",      flagEmoji = "🇪🇪"),
        AppLanguage(code = "lt", englishName = "Lithuanian", nativeName = "Lietuvių",   flagEmoji = "🇱🇹"),
        AppLanguage(code = "ro", englishName = "Romanian",   nativeName = "Română",     flagEmoji = "🇷🇴"),
        AppLanguage(code = "tr", englishName = "Turkish",    nativeName = "Türkçe",     flagEmoji = "🇹🇷")
    )

    val default: AppLanguage = all.first { it.code == "en" }

    fun byCode(code: String): AppLanguage? = all.firstOrNull { it.code == code }

    fun currentLanguageCode(locale: Locale = Locale.getDefault()): String {
        val languageCode = locale.language
        return byCode(languageCode)?.code ?: default.code
    }

    fun currentLanguageEnglishName(locale: Locale = Locale.getDefault()): String {
        return byCode(currentLanguageCode(locale))?.englishName ?: default.englishName
    }
}
