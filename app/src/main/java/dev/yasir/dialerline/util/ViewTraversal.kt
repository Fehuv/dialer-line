package dev.yasir.dialerline.util

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.util.ArrayDeque

fun View.breadthFirst(): Sequence<View> = sequence {
    val queue = ArrayDeque<View>()
    queue.add(this@breadthFirst)
    while (queue.isNotEmpty()) {
        val view = queue.removeFirst()
        yield(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                queue.addLast(view.getChildAt(index))
            }
        }
    }
}

fun View.readableText(): String? = when (this) {
    is TextView -> text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    else -> contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}
