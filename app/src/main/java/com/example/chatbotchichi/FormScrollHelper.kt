package com.example.kakaotalkautobot

import android.view.View
import androidx.core.widget.NestedScrollView

fun NestedScrollView.bindFocusScroll(vararg views: View) {
    views.forEach { view ->
        view.setOnFocusChangeListener { focusedView, hasFocus ->
            if (!hasFocus) return@setOnFocusChangeListener
            post {
                val targetY = (focusedView.top - 48).coerceAtLeast(0)
                smoothScrollTo(0, targetY)
            }
        }
    }
}
