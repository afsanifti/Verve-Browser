package com.verve.browser.data.local

import android.content.Context
import android.util.AtomicFile
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.verve.browser.presentation.browser.BrowserTab
import com.verve.browser.presentation.browser.BrowserUiState
import com.verve.browser.presentation.browser.TabStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PersistedSessionSnapshot(
    val tabs: List<BrowserTab>,
    val tabStacks: List<TabStack>,
    val activeTabId: String
)

class TabSessionStorage(context: Context) {

    private val sessionFile = AtomicFile(File(context.filesDir, "verve_tab_session_v1.json"))

    fun loadSync(): PersistedSessionSnapshot? {
        return runCatching {
            if (!sessionFile.baseFile.exists()) return null
            val rawJson = sessionFile.readFully().toString(Charsets.UTF_8)
            if (rawJson.isBlank()) return null

            val root = JSONObject(rawJson)
            val stacksJson = root.optJSONArray("stacks") ?: JSONArray()
            val tabsJson = root.optJSONArray("tabs") ?: JSONArray()

            val loadedStacks = buildList {
                for (i in 0 until stacksJson.length()) {
                    val obj = stacksJson.optJSONObject(i) ?: continue
                    val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                    add(
                        TabStack(
                            id = id,
                            title = obj.optString("title", "Tab Group"),
                            seedColor = obj.optIntOrNull("seedColor")?.let { Color(it) },
                            isCollapsed = obj.optBoolean("isCollapsed", false),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }
            val validStackIds = loadedStacks.map { it.id }.toSet()

            val loadedTabs = buildList {
                for (i in 0 until tabsJson.length()) {
                    val obj = tabsJson.optJSONObject(i) ?: continue
                    val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val rawStackId = obj.optString("stackId").takeIf { it.isNotBlank() }
                    add(
                        BrowserTab(
                            id = id,
                            url = obj.optString("url", ""),
                            title = obj.optString("title", ""),
                            isIncognito = false,
                            stackId = rawStackId?.takeIf { it in validStackIds },
                            siteSeedColor = obj.optIntOrNull("siteSeedColor")?.let { Color(it) },
                            topRegionColor = obj.optIntOrNull("topRegionColor")?.let { Color(it) },
                            bottomRegionColor = obj.optIntOrNull("bottomRegionColor")?.let { Color(it) },
                            lastAccessedAt = obj.optLong("lastAccessedAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            if (loadedTabs.isEmpty()) return null

            val savedActiveId = root.optString("activeTabId", "")
            val resolvedActiveId = loadedTabs.firstOrNull { it.id == savedActiveId }?.id
                ?: loadedTabs.maxByOrNull { it.lastAccessedAt }?.id
                ?: BrowserUiState.INITIAL_TAB_ID

            PersistedSessionSnapshot(
                tabs = loadedTabs,
                tabStacks = loadedStacks,
                activeTabId = resolvedActiveId
            )
        }.getOrNull()
    }

    suspend fun save(snapshot: PersistedSessionSnapshot) = withContext(Dispatchers.IO) {
        runCatching {
            // Never persist Incognito tabs to disk
            val normalTabs = snapshot.tabs.filter { !it.isIncognito }
            val activeNormalTabId = normalTabs.firstOrNull { it.id == snapshot.activeTabId }?.id
                ?: normalTabs.maxByOrNull { it.lastAccessedAt }?.id
                ?: BrowserUiState.INITIAL_TAB_ID

            val usedStackIds = normalTabs.mapNotNull { it.stackId }.toSet()
            val nonEmptyStacks = snapshot.tabStacks.filter { it.id in usedStackIds }

            val root = JSONObject().apply {
                put("version", 1)
                put("activeTabId", activeNormalTabId)

                put("stacks", JSONArray().apply {
                    nonEmptyStacks.forEach { stack ->
                        put(
                            JSONObject().apply {
                                put("id", stack.id)
                                put("title", stack.title)
                                stack.seedColor?.let { put("seedColor", it.toArgb()) }
                                put("isCollapsed", stack.isCollapsed)
                                put("createdAt", stack.createdAt)
                            }
                        )
                    }
                })

                put("tabs", JSONArray().apply {
                    normalTabs.forEach { tab ->
                        put(
                            JSONObject().apply {
                                put("id", tab.id)
                                put("url", tab.url)
                                put("title", tab.title)
                                tab.stackId?.let { put("stackId", it) }
                                tab.siteSeedColor?.let { put("siteSeedColor", it.toArgb()) }
                                tab.topRegionColor?.let { put("topRegionColor", it.toArgb()) }
                                tab.bottomRegionColor?.let { put("bottomRegionColor", it.toArgb()) }
                                put("lastAccessedAt", tab.lastAccessedAt)
                            }
                        )
                    }
                })
            }

            val bytes = root.toString().toByteArray(Charsets.UTF_8)
            val stream = sessionFile.startWrite()
            try {
                stream.write(bytes)
                sessionFile.finishWrite(stream)
            } catch (t: Throwable) {
                sessionFile.failWrite(stream)
                throw t
            }
        }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null
}