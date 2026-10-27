package dev.wfhdiary.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val settingsJson: String,
    val legacyJson: String?,
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}
