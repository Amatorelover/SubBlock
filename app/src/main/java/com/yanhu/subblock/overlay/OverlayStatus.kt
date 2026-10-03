package com.yanhu.subblock.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 悬浮窗服务的"体检报告"，实时广播给界面。
 *
 * 为什么需要它？因为"屏幕上没东西"这种问题，如果服务只能沉默地失败，
 * 用户就只能靠猜。让它把状态和失败原因说出来，问题一眼可见。
 *
 * 报告里刻意**只留两个字段**，因为界面真正要回答的也只有两个问题：
 *   - 屏幕上有几块？ -> [onScreen]
 *   - 有没有出错？   -> [error]
 *
 * ---------- 关于被删掉的 `running` ----------
 * 这里曾经还有一个 `running: Boolean`（字面意思是"服务是否活着"），后被删除。
 * 三个理由，每一个单独都足以判它死刑：
 *   1. 没人在读。界面只用 onScreen 和 error，这个字段写了就再没被读过。
 *   2. 它会撒谎。服务准备退出、窗口已全部摘除时，写入方仍上报 `running = true`。
 *   3. 它测不准。服务被系统强杀时，这个内存里的总线收不到任何通知，
 *      `running` 会一直停在过期的 true——比没有还糟。
 *
 * 一个既没人读、又会撒谎、还测不准的字段，留着只会误导下一个读代码的人。
 * 「服务是不是在跑」这件事，界面完全可以用 `enabled && onScreen == 0` 自己判断，
 * 不需要一个专门的信使把它讲错。
 */
data class OverlayStatus(
    /** 实际挂上屏幕的窗口数 */
    val onScreen: Int = 0,
    /** 不为 null 时表示有异常，直接展示给人看 */
    val error: String? = null
)

object OverlayStatusBus {
    private val _state = MutableStateFlow(OverlayStatus())
    val state: StateFlow<OverlayStatus> = _state

    fun update(onScreen: Int, error: String? = null) {
        _state.value = OverlayStatus(onScreen, error)
    }
}
