package starter

import screeps.api.*
import screeps.api.structures.Structure
import screeps.api.structures.StructureSpawn

fun <T> List<T>.filterOrReturnExistingIfEmpty(predicate: (T) -> Boolean): List<T> {
    val old = this
    val result = this.filter(predicate)

    if (result.isNotEmpty())
        return result

    return old
}

object CurrentGameState {
    val roomStates: MutableMap<String, CurrentRoomState> = mutableMapOf()
}

class CurrentRoomState(val room: Room) {

    val roomName: String

    val constructionSites: List<ConstructionSite>

    /** Non-controller structures with HP < HP_MAX*/
    val damagedStructures: List<Structure>

    /** One of [STRUCTURE_CONTAINER, STRUCTURE_STORAGE] */
    val energyContainers: List<Structure>

    val energyContainersTotalLevel: Int

    val energyContainersTotalMaximumLevel: Int

    val hostileCreeps: List<Creep>

    val spawnEnergyStructures: Map<String, List<Structure>>

    private val structures: Array<Structure>

    init {
        structures = room.find(FIND_STRUCTURES)
        roomName = room.name
        damagedStructures = structures.filter { !it.isStructureTypeOf(STRUCTURE_CONTROLLER) && it.hits < it.hitsMax }
        energyContainers = structures.filter { it.isEnergyContainer() }
        energyContainersTotalLevel = energyContainers.sumBy { it.unsafeCast<StoreOwner>().store.getUsedCapacity(RESOURCE_ENERGY) ?: 0 }
        energyContainersTotalMaximumLevel = energyContainers.sumBy { it.unsafeCast<StoreOwner>().store.getCapacity(RESOURCE_ENERGY) ?: 0 }
        hostileCreeps = room.find(FIND_HOSTILE_CREEPS).toList()

        val spawnEnergyStructures = mutableMapOf<String, List<Structure>>()
        structures.filter { it.isStructureTypeOf(STRUCTURE_SPAWN) }.forEach { spawn ->
            spawnEnergyStructures[spawn.id] = determineSpawnEnergyStructures(spawn as StructureSpawn)
        }
        this.spawnEnergyStructures = spawnEnergyStructures.toMap()

        constructionSites = room.find(FIND_MY_CONSTRUCTION_SITES).toList()
    }

    private fun determineSpawnEnergyStructures(spawn: StructureSpawn): List<Structure> {
        val availableEnergyStructures = structures.filter { it.isSpawnEnergyContainer() }

        return when (spawn.memory.energyStructuresDirFromToOpposite) {
            TOP_LEFT, TOP, TOP_RIGHT -> availableEnergyStructures.sortedBy { it.pos.y }
            RIGHT -> availableEnergyStructures.sortedByDescending { it.pos.x }
            BOTTOM_RIGHT, BOTTOM, BOTTOM_LEFT -> availableEnergyStructures.sortedByDescending { it.pos.y }
            LEFT -> availableEnergyStructures.sortedBy { it.pos.x }
            else -> availableEnergyStructures
        }
    }
}