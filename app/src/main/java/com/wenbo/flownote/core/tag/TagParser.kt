package com.wenbo.flownote.core.tag

/**
 * 标签解析：正文以 `#标签` 形式内联书写（flomo 风格），正文是唯一事实源。
 *
 * 字符规则：Unicode 字母/数字 + `_` `-` `/`（`/` 预留层级标签，如 #工作/项目）。
 * 非【字母数字】即终止（空白与中英文标点天然断开，URL 的 #fragment 会被误吸，v1 接受此行为）。
 */
val TAG_REGEX: Regex = Regex("""#([\p{L}\p{N}_\-/]+)""")

/** 从正文解析标签名（不含 #），去重、保序。 */
fun parseTags(content: String): List<String> =
    TAG_REGEX.findAll(content)
        .map { it.groupValues[1] }
        .distinct()
        .toList()

/** 标签在正文中的区间，用于绿色高亮渲染。 */
fun tagRanges(content: String): List<IntRange> =
    TAG_REGEX.findAll(content).map { it.range }.toList()

/**
 * 边界安全替换：仅替换完整的 `#oldName`（其后必须紧跟非标签字符或结尾），
 * 防止把 `#work` 误改到 `#workflow` 或 `#work/plan` 上。
 */
fun replaceTagOccurrences(content: String, oldName: String, newName: String): String {
    val regex = Regex("#${Regex.escape(oldName)}(?=[^\\p{L}\\p{N}_\\-/]|$)")
    return content.replace(regex, "#$newName")
}

/** 边界安全剥离 `#name`（连同其前导空格），用于删除标签时清理正文。 */
fun stripTagOccurrences(content: String, name: String): String =
    content
        .replace(Regex(" ?#${Regex.escape(name)}(?=[^\\p{L}\\p{N}_\\-/]|$)"), "")
        .removePrefix(" ")
