package com.wenbo.flownote.core.tag

import org.junit.Assert.assertEquals
import org.junit.Test

class TagParserTest {

    @Test
    fun `解析单个中文标签`() {
        assertEquals(listOf("灵感"), parseTags("今天想到一个点子 #灵感 记一下"))
    }

    @Test
    fun `解析多个标签且去重保序`() {
        assertEquals(
            listOf("工作", "灵感"),
            parseTags("#工作 开会记录 #灵感 #工作 再补充"),
        )
    }

    @Test
    fun `标点与空白终止标签`() {
        // 中文标点、英文标点、换行都应终止
        assertEquals(listOf("想法"), parseTags("#想法，这是补充"))
        assertEquals(listOf("想法"), parseTags("#想法. ok"))
        assertEquals(listOf("想法"), parseTags("#想法\n下一行"))
        assertEquals(listOf("想法"), parseTags("#想法!"))
    }

    @Test
    fun `下划线中横线保留`() {
        assertEquals(listOf("work_plan", "side-project"), parseTags("#work_plan #side-project"))
    }

    @Test
    fun `层级标签整体解析`() {
        assertEquals(listOf("工作/项目A"), parseTags("#工作/项目A 进度同步"))
    }

    @Test
    fun `行首标签`() {
        assertEquals(listOf("日记"), parseTags("#日记\n今天天气不错"))
    }

    @Test
    fun `无标签返回空`() {
        assertEquals(emptyList<String>(), parseTags("没有标签的内容"))
        assertEquals(emptyList<String>(), parseTags(""))
    }

    @Test
    fun `孤立井号不算标签`() {
        assertEquals(emptyList<String>(), parseTags("C# 语言 # 学习"))
    }

    @Test
    fun `tagRanges 给出正确区间`() {
        val content = "abc #tag def"
        val ranges = tagRanges(content)
        assertEquals(1, ranges.size)
        // "#tag" 从下标 4 到 7
        assertEquals("#tag", content.substring(ranges[0].first, ranges[0].last + 1))
    }

    @Test
    fun `replaceTagOccurrences 边界安全`() {
        // 不会误伤 #workflow
        assertEquals("#workflow", replaceTagOccurrences("#workflow", "work", "job"))
        assertEquals("#job #workflow", replaceTagOccurrences("#work #workflow", "work", "job"))
        assertEquals("#job，其他", replaceTagOccurrences("#work，其他", "work", "job"))
        assertEquals("#job", replaceTagOccurrences("#work", "work", "job"))
    }

    @Test
    fun `replaceTagOccurrences 不误伤层级标签`() {
        assertEquals("#work/plan", replaceTagOccurrences("#work/plan", "work", "job"))
    }

    @Test
    fun `stripTagOccurrences 剥离标签与尾随空格`() {
        assertEquals("前 后", stripTagOccurrences("前 #tag 后", "tag"))
        assertEquals("前", stripTagOccurrences("前 #tag", "tag"))
        assertEquals("后", stripTagOccurrences("#tag 后", "tag"))
        // 不误伤
        assertEquals("#workflow", stripTagOccurrences("#workflow", "work"))
    }
}
