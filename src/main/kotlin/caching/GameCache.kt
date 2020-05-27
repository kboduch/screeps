package caching

import screeps.api.*
import starter.*

class GameCache {
    private val targets: MutableMap<String, MutableSet<String>> = mutableMapOf()

    fun build() {
        this.cacheTargets()
    }

    fun refresh() {
        this.cacheTargets()
    }

    fun debug() {
        console.log("Targets (${targets.count()})")
        targets.forEach { entry ->
            console.log("Target (${entry.value.count()}): ${entry.key}")
            entry.value.forEach {
                console.log("Creep: $it")
            }
        }
        console.log("End Targets")
    }

    private fun cacheTargets() {

        targets.clear()

        Game.creeps.values.forEach { creep: Creep ->
            val targetId = creep.memory.targetId

            if (targetId != null) {
                targets.getOrPut(targetId, { mutableSetOf() }).add(creep.name)
            }
        }
    }
}
