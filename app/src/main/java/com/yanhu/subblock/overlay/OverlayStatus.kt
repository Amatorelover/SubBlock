package com.yanhu.subblock.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 悬浮窗服务的"体检报告"，实时广播给界面。
 *
 * 为什么需要它？因为"屏幕上没东西"这种问题，如果服务只能沉默地失败，
 * 用户就只能靠猜。让它把状态和失败原因说出来，问题一眼可见。
 */
data class OverlayStatus(
    /** 服务是否活着 */
    val running: Boolean = false,
    /** 实际挂上屏幕的窗口数 */
    val onScreen: Int = 0,
    /** 不为 null 时表示有异常，直接展示给人看 */
    val error: String? = null
)

object OverlayStatusBus {
    private val _state = MutableStateFlow(OverlayStatus())
    val state: StateFlow<OverlayStatus> = _state

    fun update(running: Boolean, onScreen: Int, error: String? = null) {
        _state.value = OverlayStatus(running, onScreen, error)
    }
}
