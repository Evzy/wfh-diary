package dev.wfhdiary.app.backup

import dev.wfhdiary.app.data.remote.UserSettingsDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class BackupDocument(
    val format: String = FORMAT,
    val version: Int = CURRENT_VERSION,
    val exportedAt: String,
    val appVersion: String,
    val settings: UserSettingsDto,
    val days: List<BackupDay> = emptyList(),
    val leavePeriods: List<BackupLeavePeriod> = emptyList(),
    val legacy: JsonObject? = null,
) {
    companion object {
        const val FORMAT = "wfh-diary-backup"
        const val CURRENT_VERSION = 1
    }
}
