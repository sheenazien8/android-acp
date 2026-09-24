package com.lakasir.acp.ui.chat

object TokenFormat {

    fun compact(tokens: Long): String = when {
        tokens >= 1_000_000 -> "%.1fM".format(tokens / 1_000_000.0).replace(".0M", "M")
        tokens >= 1_000 -> "%.1fk".format(tokens / 1_000.0).replace(".0k", "k")
        else -> tokens.toString()
    }
}
