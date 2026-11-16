// SPDX-License-Identifier: GPL-3.0-or-later
// Wiring seluruh komponen (manual DI). Tiap perubahan pengaturan diterapkan ke registry
// MCP: workspace bawaan, server remote, dan kebijakan izin.
package app.snapgent.android

import android.content.Context
import app.snapgent.mcp.BridgeRouter
import app.snapgent.mcp.LogBuffer
import app.snapgent.mcp.McpRegistry
import app.snapgent.mcp.ServerAdmin
import app.snapgent.mcp.ToolCategory
import app.snapgent.mcp.ToolOverride
import app.snapgent.mcp.ToolPolicy
import app.snapgent.remote.RemoteMcpServer
import app.snapgent.settings.AppSettings
import app.snapgent.settings.ConfigCodec
import app.snapgent.settings.RemoteServerEntry
import app.snapgent.settings.SettingsRepository
import app.snapgent.webhost.ProviderSite
import app.snapgent.webhost.SiteCatalog
import app.snapgent.webhost.WebStorage
import app.snapgent.workspace.WorkspaceServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(private val appContext: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val log = LogBuffer()
    val settingsRepo = SettingsRepository(appContext)
    val webStorage = WebStorage(appContext)
    val approvals = ApprovalCoordinator()
    val workspace = WorkspaceManager(appContext, log)
    val registry = McpRegistry(log)
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    val sites: List<ProviderSite> by lazy { SiteCatalog.load(appContext) }
    val tabs = ChatTabs(this)

    @Volatile
    var settings: AppSettings = AppSettings()
        private set

    private val workspaceServer = WorkspaceServer({ workspace.currentFs() }, log)
    private val syncMutex = Mutex()
    private val remotes = LinkedHashMap<String, RemoteHandle>()

    private class RemoteHandle(val entry: RemoteServerEntry, val server: RemoteMcpServer)

    private val admin = object : ServerAdmin {
        override suspend fun restartAll(): Boolean {
            for (server in registry.all()) {
                runCatching {
                    server.stop()
                    server.start()
                }
            }
            return registry.all().any { it.isAlive }
        }

        override suspend fun removeRemote(id: String): Boolean {
            if (settings.remoteServers.none { it.id == id }) return false
            settingsRepo.update { current ->
                current.copy(remoteServers = current.remoteServers.filterNot { it.id == id })
            }
            return true
        }
    }

    val router = BridgeRouter(
        registry = registry,
        policy = { currentPolicy() },
        approvals = approvals,
        log = log,
        admin = admin,
    )

    fun start() {
        scope.launch {
            settingsRepo.flow.collect { applySettings(it) }
        }
    }

    fun currentPolicy(): ToolPolicy = policyFor(settings)

    private suspend fun applySettings(next: AppSettings) {
        syncMutex.withLock {
            settings = next
            workspace.restore(next)

            if (next.builtinWorkspaceEnabled) {
                if (registry.get(WorkspaceServer.SERVER_ID) == null) registry.register(workspaceServer)
            } else {
                registry.unregister(WorkspaceServer.SERVER_ID)
            }

            val wanted = next.remoteServers.filter { it.enabled }.associateBy { it.id }
            for (id in remotes.keys.toList()) {
                if (wanted[id] != remotes.getValue(id).entry) {
                    registry.unregister(id)
                    remotes.remove(id)
                }
            }
            for ((id, entry) in wanted) {
                if (remotes.containsKey(id)) continue
                val server = RemoteMcpServer(entry.id, entry.name, entry.url, entry.headers, http)
                remotes[id] = RemoteHandle(entry, server)
                registry.register(server)
                scope.launch {
                    server.start()
                    if (server.isAlive) {
                        log.info("mcp", "${entry.name}: ${server.tools.size} tool")
                    } else {
                        log.warn("mcp", "${entry.name}: ${server.error}")
                    }
                }
            }
        }
    }

    /** Uji koneksi satu server remote tanpa mengubah registry aktif. */
    suspend fun testRemote(entry: RemoteServerEntry): String {
        val probe = RemoteMcpServer(entry.id, entry.name, entry.url, entry.headers, http)
        return try {
            probe.start()
            if (probe.isAlive) {
                "OK: ${probe.tools.size} tool" +
                    probe.tools.take(6).joinToString(prefix = " (", postfix = ")") { it.name }
            } else {
                "Gagal: ${probe.error}"
            }
        } finally {
            probe.stop()
        }
    }

    suspend fun restartAll(): Boolean = admin.restartAll()

    fun exportConfig(): String = ConfigCodec.export(settings, webStorage.exportAll())

    suspend fun importConfig(text: String) {
        val file = ConfigCodec.import(text)
        settingsRepo.replace(file.settings)
        webStorage.replaceAll(file.webStorage)
    }

    private fun policyFor(s: AppSettings): ToolPolicy = ToolPolicy(
        enabledCategories = s.enabledCategories.mapNotNull { ToolCategory.fromWire(it) }.toSet(),
        confirmCategories = s.confirmCategories.mapNotNull { ToolCategory.fromWire(it) }.toSet(),
        reviewResults = !s.autoSendResults,
        maxResponseChars = s.maxResponseChars,
        toolOverrides = s.toolOverrides.mapNotNull { (name, mode) ->
            ToolOverride.fromWire(mode)?.let { name to it }
        }.toMap(),
    )
}
