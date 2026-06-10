package me.rerere.rikkahub.ui.hooks

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs

@Composable
fun ImeLazyListAutoScroller(
    lazyListState: LazyListState,
) {
    val ime = WindowInsets.ime
    val localDensity = LocalDensity.current
    var imeHeigh by remember { mutableIntStateOf(0) }
    // 上次执行 scrollBy 时记录的键盘高度，用于阈值过滤
    var lastScrollPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        snapshotFlow {
            ime.getBottom(localDensity)
        }.collect { keyboardHeight ->
            if (keyboardHeight > 0) {
                // 键盘打开中：仅在高度增量超过阈值时才执行 scrollBy，
                // 避免键盘动画每帧触发 LazyColumn 重布局
                if (keyboardHeight > imeHeigh) {
                    val delta = keyboardHeight - imeHeigh
                    if (abs(keyboardHeight - lastScrollPx) >= 50) {
                        lazyListState.scrollBy(delta.toFloat())
                        lastScrollPx = keyboardHeight
                    }
                }
                imeHeigh = keyboardHeight
            } else {
                // 键盘完全关闭：重置状态，不做 scrollBy（contentPadding 自然回位）
                imeHeigh = 0
                lastScrollPx = 0
            }
        }
    }
}
