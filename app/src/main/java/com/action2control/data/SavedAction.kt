package com.action2control.data

import java.util.UUID

/**
 * 保存的动作序列
 */
data class SavedAction(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val actions: List<String>,
    val loopMode: LoopMode = LoopMode.INFINITE,
    val loopCount: Int = 1,
    val videoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 循环模式
 */
enum class LoopMode(val label: String) {
    INFINITE("无限循环"),
    COUNT("指定次数")
}

/**
 * 序列化/反序列化辅助
 */
object ActionSerializer {
    private const val ACTION_SEPARATOR = "|||"
    private const val ITEM_SEPARATOR = ":::"

    fun serialize(action: SavedAction): String {
        return listOf(
            action.id,
            action.name,
            action.actions.joinToString(ITEM_SEPARATOR),
            action.loopMode.name,
            action.loopCount.toString(),
            action.videoPath ?: "",
            action.createdAt.toString()
        ).joinToString(ACTION_SEPARATOR)
    }

    fun deserialize(data: String): SavedAction? {
        return try {
            val parts = data.split(ACTION_SEPARATOR)
            if (parts.size < 7) return null

            SavedAction(
                id = parts[0],
                name = parts[1],
                actions = parts[2].split(ITEM_SEPARATOR).filter { it.isNotEmpty() },
                loopMode = LoopMode.valueOf(parts[3]),
                loopCount = parts[4].toInt(),
                videoPath = parts[5].takeIf { it.isNotEmpty() },
                createdAt = parts[6].toLong()
            )
        } catch (e: Exception) {
            null
        }
    }
}
