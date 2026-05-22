package com.action2control.ml

import android.util.Log

/**
 * 动作序列提取器
 * 
 * 功能:
 * 1. 合并连续的相同动作 (去重)
 * 2. 过滤持续时间过短的噪声动作 (去抖)
 * 
 * 例如:
 * 输入:  [swipe_up, swipe_up, swipe_up, like, swipe_up, swipe_up, swipe_up, swipe_up, swipe_up, back]
 * 输出:  [swipe_up, swipe_up, swipe_up, back]  (minFrames=5 时)
 * 
 * 处理流程:
 * 1. 将连续相同动作分组: [(swipe_up×3), (like×1), (swipe_up×6), (back×1)]
 * 2. 过滤掉帧数 < minFrames 的组: [(swipe_up×3), (swipe_up×6)] → like 被过滤
 * 3. 合并剩余组的动作标签: [swipe_up, swipe_up]
 */
object ActionSequenceExtractor {

    private const val TAG = "ActionSequenceExtractor"
    private const val DEFAULT_MIN_FRAMES = 5

    /**
     * 数据类：表示一个连续动作段
     * 
     * @param action 动作名称
     * @param frameCount 持续帧数
     */
    data class ActionSegment(
        val action: String,
        val frameCount: Int
    )

    /**
     * 提取去抖后的动作序列
     * 
     * @param actionList 原始动作分类结果列表 (每帧一个动作标签)
     * @param minFrames 最小持续帧数，低于此值的动作段将被过滤 (默认 5)
     * @return 去抖后的动作序列
     */
    fun extractSequence(
        actionList: List<String>,
        minFrames: Int = DEFAULT_MIN_FRAMES
    ): List<String> {
        if (actionList.isEmpty()) {
            Log.d(TAG, "Empty action list, returning empty sequence")
            return emptyList()
        }

        // 步骤 1: 将连续相同动作分组
        val segments = groupConsecutiveActions(actionList)
        Log.d(TAG, "Grouped into ${segments.size} segments: ${segments.map { "${it.action}×${it.frameCount}" }}")

        // 步骤 2: 过滤噪声段
        val filteredSegments = segments.filter { it.frameCount >= minFrames }
        Log.d(TAG, "Filtered to ${filteredSegments.size} segments (minFrames=$minFrames)")

        // 步骤 3: 提取动作标签
        val result = filteredSegments.map { it.action }
        Log.i(TAG, "Final action sequence: $result")

        return result
    }

    /**
     * 将连续相同动作分组
     * 
     * 例如: [A, A, A, B, A, A, C] → [(A×3), (B×1), (A×2), (C×1)]
     */
    private fun groupConsecutiveActions(actionList: List<String>): List<ActionSegment> {
        if (actionList.isEmpty()) return emptyList()

        val segments = mutableListOf<ActionSegment>()
        var currentAction = actionList[0]
        var currentCount = 1

        for (i in 1 until actionList.size) {
            if (actionList[i] == currentAction) {
                currentCount++
            } else {
                segments.add(ActionSegment(currentAction, currentCount))
                currentAction = actionList[i]
                currentCount = 1
            }
        }
        // 添加最后一段
        segments.add(ActionSegment(currentAction, currentCount))

        return segments
    }

    /**
     * 获取动作序列的统计信息
     * 
     * @param actionList 原始动作列表
     * @return 统计信息字符串
     */
    fun getStatistics(actionList: List<String>): String {
        if (actionList.isEmpty()) return "No actions"

        val totalFrames = actionList.size
        val uniqueActions = actionList.toSet()
        val actionCounts = actionList.groupingBy { it }.eachCount()

        return buildString {
            append("Total frames: $totalFrames\n")
            append("Unique actions: ${uniqueActions.joinToString()}\n")
            append("Action distribution:\n")
            actionCounts.entries.sortedByDescending { it.value }.forEach { (action, count) ->
                append("  $action: $count frames (${String.format("%.1f", count * 100.0 / totalFrames)}%)\n")
            }
        }
    }
}
