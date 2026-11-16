// SPDX-License-Identifier: GPL-3.0-or-later
// Registry server MCP. Mengatur nama tool yang terlihat AI (dengan prefix server
// bila nama bentrok) dan routing panggilan, mirip logika _rebuild_catalog di bridge.py.
package app.snapgent.mcp

class McpRegistry(private val log: LogBuffer) {
    private val servers = LinkedHashMap<String, McpServer>()

    @Synchronized
    fun register(server: McpServer) {
        servers[server.id] = server
    }

    /** Hentikan dan lepas server. Mengembalikan true bila server ada. */
    suspend fun unregister(id: String): Boolean {
        val server = synchronized(this) { servers.remove(id) } ?: return false
        runCatching { server.stop() }
        return true
    }

    @Synchronized
    fun get(id: String): McpServer? = servers[id]

    @Synchronized
    fun all(): List<McpServer> = servers.values.toList()

    /** Start semua server (paralel per server; satu server gagal tidak menghentikan yang lain). */
    suspend fun startAll() {
        for (server in all()) {
            try {
                server.start()
                log.info("mcp", "${server.id}: ${if (server.isAlive) "aktif (${server.tools.size} tool)" else "tidak aktif: ${server.error}"}")
            } catch (e: Exception) {
                log.error("mcp", "${server.id}: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** Daftar tool yang dilihat AI: hanya dari server yang aktif. */
    fun catalog(): List<ExposedTool> {
        val alive = all().filter { it.isAlive }
        val entries = alive.flatMap { server -> server.tools.map { server to it } }
        val counts = HashMap<String, Int>()
        for ((_, tool) in entries) counts[tool.name] = (counts[tool.name] ?: 0) + 1
        return entries.map { (server, tool) ->
            val exposed = if ((counts[tool.name] ?: 0) > 1) "${server.id}/${tool.name}" else tool.name
            ExposedTool(exposed, tool, server)
        }
    }

    /**
     * Resolusi nama dari AI. Format "server/nama" selalu dipakai apa adanya. Nama polos
     * dicari di server [preferredServer] dulu, lalu di seluruh katalog bila unik.
     */
    fun resolve(name: String, preferredServer: String?): ExposedTool? {
        val catalog = catalog()
        catalog.firstOrNull { it.exposedName == name }?.let { return it }
        if (name.contains('/')) {
            val serverId = name.substringBefore('/')
            val bare = name.substringAfter('/')
            return catalog.firstOrNull { it.server.id == serverId && it.tool.name == bare }
        }
        if (!preferredServer.isNullOrEmpty()) {
            catalog.firstOrNull { it.server.id == preferredServer && it.tool.name == name }?.let { return it }
        }
        val matches = catalog.filter { it.tool.name == name }
        return if (matches.size == 1) matches.first() else null
    }
}
