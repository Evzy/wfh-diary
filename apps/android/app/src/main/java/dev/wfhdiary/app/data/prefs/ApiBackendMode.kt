package dev.wfhdiary.app.data.prefs

enum class ApiBackendMode(val storageKey: String) {
    API_LOCAL("local"),
    API_AZURE("azure"),
    ;

    companion object {
        fun fromStorageKey(raw: String?): ApiBackendMode {
            val t = raw?.trim().orEmpty()
            if (t.isEmpty()) return API_LOCAL
            return entries.find { it.storageKey == t } ?: API_LOCAL
        }
    }
}
