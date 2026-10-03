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

/**
 * 状态总线。
 *
 * ---------- 为什么不再有一个 `update(onScreen, error = null)` ----------
 * 这里原本只有一个方法：`update(onScreen: Int, error: String? = null)`。
 * 看着很简洁，却藏着一个致命缺陷——**"改数字"和"清空错误"被绑成了同一个动作**，
 * 而 `error` 的默认值是 `null`，于是"清空错误"变成了一件**顺手就发生**的事：
 *
 *   OverlayStatusBus.update(onScreen = holders.size)   // 本意只是报个数量
 *                                                      // 实际把 error 也抹成了 null
 *
 * 后果很具体：`addBlock` 失败时写下的那条错误，会被同一轮 `refresh()` 末尾的
 * `update(onScreen = ...)` 立刻擦掉——**写进去几微秒就被自己删了**，
 * 界面上那张错误卡片（HomeScreen 里的 ServiceStatusCard）根本没机会显示。
 * 一句"只是报个数量"的代码，悄悄毁掉了整个错误上报机制。
 *
 * 修法不是"记得别传 error"，而是**把这个签名拆掉**：
 *   - [setOnScreen] 只碰数量
 *   - [reportError] 只写错误
 *   - [clearError]  只清错误，且必须显式调用
 *
 * 三个动词各管一件事，"顺手"就再也清不掉错误了。
 * 这比"约定成俗地小心"可靠得多：**让错误在语法上无法悄悄发生，而不是靠人记得。**
 */
object OverlayStatusBus {
    private val _state = MutableStateFlow(OverlayStatus())
    val state: StateFlow<OverlayStatus> = _state

    /** 只更新"屏幕上有几块"，绝不触碰 [OverlayStatus.error] */
    fun setOnScreen(count: Int) {
        if (_state.value.onScreen == count) return
        _state.value = _state.value.copy(onScreen = count)
    }

    /** 写入一条错误。它会**粘住**，直到 [clearError] 被显式调用 */
    fun reportError(message: String) {
        if (_state.value.error == message) return
        _state.value = _state.value.copy(error = message)
    }

    /**
     * 清除错误。
     *
     * 调用它的地方应当只有两类，且都有明确理由：
     *   1. 服务已停：错误所指的状态不复存在，留着只会误导。
     *   2. 正要重新去挂窗口：清掉上一轮的残影，让 [reportError] 写下的
     *      永远是"最近一次真实尝试"的结果。
     * 除此之外任何地方调用它，都是在擦掉用户还没看到的线索。
     */
    fun clearError() {
        if (_state.value.error == null) return
        _state.value = _state.value.copy(error = null)
    }
}
