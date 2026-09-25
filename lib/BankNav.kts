object BankNav {
    fun parseTile(raw: String): Tile? {
        val parts = raw.split(",").map { it.trim() }
        if (parts.size != 3) return null
        val x = parts[0].toIntOrNull() ?: return null
        val y = parts[1].toIntOrNull() ?: return null
        val z = parts[2].toIntOrNull() ?: return null
        if (z !in 0..3) return null
        return Tile(x, y, z)
    }

    fun bankWindowOpen(): Boolean {
        return bankIsOpen() || interfaceOpen(11)
    }

    fun Script.nearestBankBooth(): WorldObject? {
        val pos = getMyPlayerPosition()
        return getNearbyObjects()
            .filter { it.containsOption("bank") != -1 }
            .minByOrNull { Utils.distance(pos, it) }
    }

    suspend fun Script.ensureBankOpen(tile: Tile?): Boolean {
        if (bankWindowOpen()) return true
        val pos = getMyPlayerPosition()
        if (tile != null && Utils.distance(pos, tile) > 10) {
            walkTo(tile)
            delay(600)
            delayUntil(30000) { !isWalking() }
            return false
        }
        nearestBankBooth()?.let {
            clickObject(it, it.containsOption("bank"))
            delayUntil(15000) { bankWindowOpen() }
            return bankWindowOpen()
        }
        getFilteredNPCs("banker").minByOrNull {
            Utils.distance(pos, Tile(it.endX.toInt() + getBaseX(), it.endZ.toInt() + getBaseY()))
        }?.let {
            clickNPC(it, "bank")
            delayUntil(15000) { bankWindowOpen() }
            return bankWindowOpen()
        }
        getNearbyObjects().filter { it.containsOption("deposit") != -1 }
            .minByOrNull { Utils.distance(pos, it) }?.let {
                clickObject(it, it.containsOption("deposit"))
                delayUntil(15000) { bankWindowOpen() }
                return bankWindowOpen()
            }
        return false
    }

    fun Script.bankDebugLine(): String {
        val pos = runCatching { getMyPlayerPosition() }.getOrNull()
        val objs = runCatching { getNearbyObjects() }.getOrElse {
            return "pos=${pos?.x},${pos?.y},${pos?.z} objs threw ${it::class.java.simpleName}"
        }
        val line = StringBuilder("pos=${pos?.x},${pos?.y},${pos?.z} objs=${objs.size}")
        objs.filter {
            runCatching { it.getDefinitions().name }.getOrNull()?.contains("bank", ignoreCase = true) == true
        }.take(5).forEach { o ->
            val name = runCatching { o.getDefinitions().name }.getOrNull()
            val actions = runCatching { o.getDefinitions().actions?.toList() }.getOrNull()
            val opt = runCatching { o.containsOption("bank") }.getOrNull()
            val dist = runCatching { getDistanceTo(o) }.getOrNull()
            line.append(" [id=${o.id} name=$name x=${o.x} y=${o.y} actions=$actions optBank=$opt dist=$dist]")
        }
        val closest = runCatching { nearestBankBooth() }.getOrNull()
        line.append(" straightBank=${closest?.let { "${it.id}@${it.x},${it.y} opt=${it.containsOption("bank")}" }}")
        val bankers = runCatching { getFilteredNPCsContaining("bank") }.getOrNull()
        line.append(" bankers=${bankers?.size}")
        bankers?.take(3)?.forEach { n ->
            val nname = runCatching { n.definitions!!.name }.getOrNull()
            val ndist = runCatching { getDistanceTo(n) }.getOrNull()
            line.append(" [npc=$nname dist=$ndist]")
        }
        return line.toString()
    }
}
