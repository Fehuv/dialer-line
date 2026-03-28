package dev.yasir.dialerline.ui

import android.view.View

interface DialpadAnchorStrategy {
    fun isDialpadVisible(root: View): Boolean
}
