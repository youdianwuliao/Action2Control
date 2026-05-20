package com.action2control.ml

import org.junit.Assert.*
import org.junit.Test

/**
 * ActionSequenceExtractor 单元测试
 * 
 * 测试覆盖:
 * 1. 空列表处理
 * 2. 单个动作
 * 3. 连续相同动作合并
 * 4. 噪声过滤
 * 5. 边界情况 (正好等于 minFrames)
 * 6. 全部噪声被过滤
 * 7. 统计信息
 */
class ActionSequenceExtractorTest {

    @Test
    fun `empty list returns empty sequence`() {
        val result = ActionSequenceExtractor.extractSequence(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single action returns single action`() {
        val input = listOf("swipe_up")
        val result = ActionSequenceExtractor.extractSequence(input, minFrames = 1)
        assertEquals(listOf("swipe_up"), result)
    }

    @Test
    fun `single action below minFrames is filtered`() {
        val input = listOf("swipe_up")
        val result = ActionSequenceExtractor.extractSequence(input, minFrames = 5)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `consecutive same actions are merged`() {
        val input = listOf("swipe_up", "swipe_up", "swipe_up", "swipe_up", "swipe_up")
        val result = ActionSequenceExtractor.extractSequence(input, minFrames = 1)
        assertEquals(listOf("swipe_up"), result)
    }

    @Test
    fun `noise action is filtered out`() {
        // swipe_up×5, like×2, swipe_down×5
        val input = listOf(
            "swipe_up", "swipe_up", "swipe_up", "swipe_up", "swipe_up",
            "like", "like",
            "swipe_down", "swipe_down", "swipe_down", "swipe_down", "swipe_down"
        )
        val result = ActionSequenceExtractor.extractSequence(input, minFrames = 5)
        assertEquals(listOf("swipe_up", "swipe_down"), result)
    }

    @Test
    fun `boundary case exactly at minFrames`() {
        // 正好 5 帧，应该保留
        val input = listOf("like", "like", "like", "like", "like")
        val result = ActionSequenceExtractor.extractSequence(input, minFrames = 5)
        assertEquals(listOf("like"), result)
    }

    @Test
    fun `boundary case one below minFrames`() {
        // 4 帧，minFrames=5，应该过滤
        val input = listOf("like", "like", "like", "like")
        val result = ActionSequenceExtractor.extractSequence(input, minFrames = 5)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `all noise filtered returns empty`() {
        val input = listOf("like", "like", "back", "back", "back")
        val result = ActionSequenceExtractor.extractSequence(input, minFrames = 5)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `multiple valid segments preserved in order`() {
        // swipe_up×10, like×3, swipe_down×8, back×2, swipe_up×6
        val input = buildList {
            addAll(List(10) { "swipe_up" })
            addAll(List(3) { "like" })
            addAll(List(8) { "swipe_down" })
            addAll(List(2) { "back" })
            addAll(List(6) { "swipe_up" })
        }
        val result = ActionSequenceExtractor.extractSequence(input, minFrames = 5)
        assertEquals(listOf("swipe_up", "swipe_down", "swipe_up"), result)
    }

    @Test
    fun `unknown actions are treated like any other action`() {
        val input = buildList {
            addAll(List(6) { "unknown" })
            addAll(List(3) { "swipe_up" })
            addAll(List(7) { "unknown" })
        }
        val result = ActionSequenceExtractor.extractSequence(input, minFrames = 5)
        assertEquals(listOf("unknown", "unknown"), result)
    }

    @Test
    fun `default minFrames is 5`() {
        val input = buildList {
            addAll(List(5) { "swipe_up" })
            addAll(List(4) { "like" })
        }
        // 使用默认 minFrames
        val result = ActionSequenceExtractor.extractSequence(input)
        assertEquals(listOf("swipe_up"), result)
    }

    @Test
    fun `getStatistics returns correct info`() {
        val input = listOf(
            "swipe_up", "swipe_up", "swipe_up",
            "like", "like",
            "swipe_down"
        )
        val stats = ActionSequenceExtractor.getStatistics(input)
        
        assertTrue(stats.contains("Total frames: 6"))
        assertTrue(stats.contains("swipe_up: 3 frames"))
        assertTrue(stats.contains("like: 2 frames"))
        assertTrue(stats.contains("swipe_down: 1 frames"))
    }

    @Test
    fun `getStatistics handles empty list`() {
        val stats = ActionSequenceExtractor.getStatistics(emptyList())
        assertEquals("No actions", stats)
    }

    @Test
    fun `real world scenario swipe video`() {
        // 模拟真实场景: 用户先举手 (swipe_up)，然后点赞 (like)，然后返回 (back)
        val input = buildList {
            // 准备动作 (噪声，过滤掉)
            addAll(List(3) { "unknown" })
            // 上滑动作 (持续 2 秒 @10fps = 20 帧)
            addAll(List(20) { "swipe_up" })
            // 过渡噪声
            addAll(List(2) { "unknown" })
            // 点赞动作 (持续 1 秒 = 10 帧)
            addAll(List(10) { "like" })
            // 过渡噪声
            addAll(List(4) { "unknown" })
            // 返回动作 (持续 1.5 秒 = 15 帧)
            addAll(List(15) { "back" })
        }

        val result = ActionSequenceExtractor.extractSequence(input, minFrames = 5)
        assertEquals(listOf("swipe_up", "like", "back"), result)
    }
}
