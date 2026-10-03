package com.yanhu.subblock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 全局唯一的 DataStore 实例，绑在 Application Context 上 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "subblock")

/**
 * 设置仓库。
 *
 * 这里是整个应用**唯一的数据源头**：
 * 界面改了数据 -> DataStore 变化 -> 悬浮窗服务收到通知并重画。
 * 反过来悬浮窗拖动后写回 DataStore -> 界面上的数值也跟着变。
 * 这样就不用写任何"界面和服务之间互相通知"的胶水代码。
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val BLOCKS = stringPreferencesKey("blocks")
    }

    /** 总开关 */
    val enabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ENABLED] ?: false }

    /** 所有遮挡块 */
    val blocks: Flow<List<BlockConfig>> =
        context.dataStore.data.map { BlockConfig.listFromJson(it[Keys.BLOCKS]) }

    suspend fun setEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.ENABLED] = value }
    }

    /** 整份替换（新建预设、导入配置时用）。同样经由 [mutate]，几何数据一样会被收束。 */
    suspend fun saveBlocks(list: List<BlockConfig>) = mutate { list }

    /** 新增或更新一个遮挡块 */
    suspend fun upsert(block: BlockConfig) = mutate { list ->
        val i = list.indexOfFirst { it.id == block.id }
        if (i >= 0) {
            list.toMutableList().also { it[i] = block }
        } else {
            list + block
        }
    }

    suspend fun remove(id: String) = mutate { list -> list.filterNot { it.id == id } }

    /** 只更新几何信息，样式保持不变 */
    suspend fun updateGeometry(id: String, nx: Float, ny: Float, nw: Float, nh: Float) =
        mutate { list ->
            list.map { if (it.id == id) it.copy(nx = nx, ny = ny, nw = nw, nh = nh) else it }
        }

    /**
     * 所有写入的**公共出口**。
     *
     * 不管数据从哪条路来——界面编辑、快捷定位、拖动落盘、导入解析——
     * 落盘前都在这里过一遍 [BlockConfig.constrained]。
     * 于是「磁盘里永远是合法数据」是一条结构保证，
     * 而不是靠每个入口各自记得打补丁（那正是越界 bug 的成因：
     * 同一件事有好几个入口，改的人只加固了其中一个）。
     */
    private suspend fun mutate(f: (List<BlockConfig>) -> List<BlockConfig>) {
        context.dataStore.edit { prefs ->
            val current = BlockConfig.listFromJson(prefs[Keys.BLOCKS])
            prefs[Keys.BLOCKS] = BlockConfig.listToJson(f(current).map { it.constrained() })
        }
    }
}
