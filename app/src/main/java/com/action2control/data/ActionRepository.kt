package com.action2control.data

import android.content.Context
import android.content.SharedPreferences
import com.action2control.data.ActionSerializer

/**
 * 动作库存储
 * 使用 SharedPreferences 存储动作序列列表
 */
class ActionRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "action_library"
        private const val KEY_ACTION_LIST = "action_list"
        private const val KEY_ACTION_COUNT = "action_count"
    }

    /**
     * 获取所有保存的动作
     */
    fun getAllActions(): List<SavedAction> {
        val count = prefs.getInt(KEY_ACTION_COUNT, 0)
        val actions = mutableListOf<SavedAction>()

        for (i in 0 until count) {
            val key = "${KEY_ACTION_LIST}_$i"
            val data = prefs.getString(key, null)
            if (data != null) {
                val action = ActionSerializer.deserialize(data)
                if (action != null) {
                    actions.add(action)
                }
            }
        }

        return actions.sortedByDescending { it.createdAt }
    }

    /**
     * 保存新动作
     */
    fun saveAction(action: SavedAction) {
        val count = prefs.getInt(KEY_ACTION_COUNT, 0)
        val key = "${KEY_ACTION_LIST}_$count"

        prefs.edit()
            .putString(key, ActionSerializer.serialize(action))
            .putInt(KEY_ACTION_COUNT, count + 1)
            .apply()
    }

    /**
     * 删除动作
     */
    fun deleteAction(actionId: String) {
        val count = prefs.getInt(KEY_ACTION_COUNT, 0)
        val actions = mutableListOf<Pair<String, String>>()

        // 读取所有动作
        for (i in 0 until count) {
            val key = "${KEY_ACTION_LIST}_$i"
            val data = prefs.getString(key, null)
            if (data != null) {
                val action = ActionSerializer.deserialize(data)
                if (action != null && action.id != actionId) {
                    actions.add(key to data)
                }
            }
        }

        // 重建存储
        val editor = prefs.edit()
        editor.putInt(KEY_ACTION_COUNT, actions.size)

        // 清除旧的
        for (i in 0 until count) {
            editor.remove("${KEY_ACTION_LIST}_$i")
        }

        // 写入新的
        for ((index, pair) in actions.withIndex()) {
            editor.putString("${KEY_ACTION_LIST}_$index", pair.second)
        }

        editor.apply()
    }

    /**
     * 更新动作
     */
    fun updateAction(action: SavedAction) {
        val count = prefs.getInt(KEY_ACTION_COUNT, 0)

        for (i in 0 until count) {
            val key = "${KEY_ACTION_LIST}_$i"
            val data = prefs.getString(key, null)
            if (data != null) {
                val existing = ActionSerializer.deserialize(data)
                if (existing?.id == action.id) {
                    prefs.edit()
                        .putString(key, ActionSerializer.serialize(action))
                        .apply()
                    return
                }
            }
        }
    }
}
