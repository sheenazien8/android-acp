package com.lakasir.acp.ui.chat

enum class DiffLineType { CONTEXT, ADDED, REMOVED }

data class DiffLine(val type: DiffLineType, val text: String)

object LineDiff {
    private const val MAX_CELLS = 400_000

    fun compute(oldText: String?, newText: String): List<DiffLine> {
        val old = oldText?.lines().orEmpty()
        val new = newText.lines()
        if (old.isEmpty()) return new.map { DiffLine(DiffLineType.ADDED, it) }
        if (old.size.toLong() * new.size > MAX_CELLS) {
            return old.map { DiffLine(DiffLineType.REMOVED, it) } + new.map { DiffLine(DiffLineType.ADDED, it) }
        }

        val lcs = Array(old.size + 1) { IntArray(new.size + 1) }
        for (i in old.indices.reversed()) {
            for (j in new.indices.reversed()) {
                lcs[i][j] = if (old[i] == new[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }

        val result = ArrayList<DiffLine>(old.size + new.size)
        var i = 0
        var j = 0
        while (i < old.size && j < new.size) {
            when {
                old[i] == new[j] -> {
                    result += DiffLine(DiffLineType.CONTEXT, old[i])
                    i++
                    j++
                }
                lcs[i + 1][j] >= lcs[i][j + 1] -> result += DiffLine(DiffLineType.REMOVED, old[i++])
                else -> result += DiffLine(DiffLineType.ADDED, new[j++])
            }
        }
        while (i < old.size) result += DiffLine(DiffLineType.REMOVED, old[i++])
        while (j < new.size) result += DiffLine(DiffLineType.ADDED, new[j++])
        return result
    }
}
