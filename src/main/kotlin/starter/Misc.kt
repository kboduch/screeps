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
    var assaultTargetRoomName: String? = null
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

    val myStructures: List<Structure>

    val droppedEnergyResources: List<Resource>

    val activeEnergySources: List<Source>

    val tombstones: List<Tombstone>

    private val structures: Array<Structure>
    private val droppedResources: List<Resource>

    init {
        structures = room.find(FIND_STRUCTURES)
        myStructures = structures.filter { it.my }
        roomName = room.name
        damagedStructures = structures.filter { !it.isStructureTypeOf(STRUCTURE_CONTROLLER) && it.hits < it.hitsMax }
        energyContainers = structures.filter { it.isEnergyContainer() }
        energyContainersTotalLevel = energyContainers.sumBy { it.unsafeCast<StoreOwner>().store.getUsedCapacity(RESOURCE_ENERGY)!! }
        energyContainersTotalMaximumLevel = energyContainers.sumBy { it.unsafeCast<StoreOwner>().store.getCapacity(RESOURCE_ENERGY)!! }
        hostileCreeps = room.find(FIND_HOSTILE_CREEPS).toList()

        val spawnEnergyStructures = mutableMapOf<String, List<Structure>>()
        structures.filter { it.isStructureTypeOf(STRUCTURE_SPAWN) }.forEach { spawn ->
            spawnEnergyStructures[spawn.id] = determineSpawnEnergyStructures(spawn as StructureSpawn)
        }
        this.spawnEnergyStructures = spawnEnergyStructures.toMap()

        constructionSites = room.find(FIND_MY_CONSTRUCTION_SITES).toList()

        tombstones = room.find(FIND_TOMBSTONES).toList()

        droppedResources = room.find(FIND_DROPPED_RESOURCES).toList()
        droppedEnergyResources = droppedResources.filter { it.resourceType == RESOURCE_ENERGY }

        activeEnergySources = room.find(FIND_SOURCES_ACTIVE).toList()
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

class WeightedStructureTypeComparator(private val weightMap: Map<StructureConstant, Int>): Comparator<Structure> {
    override fun compare(a: Structure, b: Structure): Int = when {
        weightMap[a.structureType] > weightMap[b.structureType] -> 1
        weightMap[a.structureType] < weightMap[b.structureType] -> -1
        else -> 0
    }
}

fun creepNameGenerator(role: Role, bodyPartsCost: Int): String {
    return "${role.name.substring(0, 4)}_${bodyPartsCost}_${Game.time.toString().takeLast(4)}"
}

fun isIdTargetedByCreeps(id: String): Boolean {
    return Game.creeps.values.count { it.memory.targetId == id } > 0
}
