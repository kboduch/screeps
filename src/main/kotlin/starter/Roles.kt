package starter

import screeps.api.*
import screeps.api.structures.Structure
import screeps.api.structures.StructureController
import screeps.api.structures.StructureSpawn


enum class Role {
    UNASSIGNED,
    HARVESTER,
    BUILDER,
    UPGRADER,
    REPAIRER,
    TRUCKER,
    ASSAULTER,
    RBUILDER
}

fun Creep.assault(targetRoomName: String = this.room.name) {
    if (this.pos.roomName != targetRoomName) {
        //todo optimize
        val route = Game.map.findRoute(this.pos.roomName, targetRoomName);
        if(route.value != null && route.value!!.isNotEmpty()) {
            console.log("Now heading to room ${route.value!![0].room}")
            val exit = this.pos.findClosestByRange(route.value!![0].exit)
            this.moveTo(
                    exit!!,
                    options {
                        visualizePathStyle = options {
                            lineStyle = LINE_STYLE_SOLID
                        }
                    }
            )

            return
        }

        console.log("No route found to $targetRoomName")

        return
    }

    val target = Game.getObjectById<Structure>(this.memory.targetId)
    if (null != target) {

        if (target.isStructureTypeOf(STRUCTURE_CONTROLLER)) {

            when (claimController(target as StructureController)) {
                OK -> return
                ERR_INVALID_TARGET -> {} //The target is not a valid neutral controller object.
                ERR_FULL -> {} //todo
                ERR_NOT_IN_RANGE -> moveTo(target.pos, options { visualizePathStyle = options { lineStyle = LINE_STYLE_SOLID } })
                ERR_NO_BODYPART -> { console.log("No claim body part"); return }
                ERR_GCL_NOT_ENOUGH -> { console.log("GCL is low"); return }
            }

            when (attackController(target as StructureController)) {
                ERR_NOT_IN_RANGE -> moveTo(target.pos, options { visualizePathStyle = options { lineStyle = LINE_STYLE_SOLID } })
                ERR_INVALID_TARGET -> this.memory.targetId = null
                ERR_TIRED -> this.memory.targetId = null
            }
        }
        if (!target.isStructureTypeOf(STRUCTURE_CONTROLLER)) {
            when (attack(target)) {
                ERR_NOT_IN_RANGE -> moveTo(target.pos, options { visualizePathStyle = options { lineStyle = LINE_STYLE_SOLID } })
                ERR_INVALID_TARGET -> this.memory.targetId = null
            }
        }

        return
    } else {
        this.memory.targetId = null
        val hostileCreepsInRange = this.pos.findInRange(FIND_HOSTILE_CREEPS, 5)
        val hostileStructures = this.room.find(FIND_HOSTILE_STRUCTURES)
        val otherHostileStructures = hostileStructures.filter { !it.isStructureTypeOf(STRUCTURE_CONTROLLER) }
        val hostileController = hostileStructures.filter { it.isStructureTypeOf(STRUCTURE_CONTROLLER) && (it.unsafeCast<StructureController>().upgradeBlocked < 100 || it.unsafeCast<StructureController>().upgradeBlocked == null) }
        val hostileTowers = hostileStructures.filter { it.isStructureTypeOf(STRUCTURE_TOWER) }
        val hostileSpawn = hostileStructures.filter { it.isStructureTypeOf(STRUCTURE_SPAWN) }
        val neutralStructures = this.room.find(FIND_STRUCTURES)

        if (neutralStructures.isNotEmpty()) {
            val nControllers = neutralStructures.filter { it.isStructureTypeOf(STRUCTURE_CONTROLLER) && !it.my }
            if (nControllers.isNotEmpty()) {
                this.memory.targetId = nControllers.first().id
            }
        }

        if (hostileController.isNotEmpty()) {
            this.memory.targetId = hostileController.minBy { it.pos.getRangeTo(this) }!!.id

            return
        }

        if (hostileTowers.isNotEmpty()) {
            this.memory.targetId = hostileTowers.minBy { it.pos.getRangeTo(this) }!!.id

            return
        }

        if (hostileSpawn.isNotEmpty()) {
            this.memory.targetId = hostileSpawn.minBy { it.pos.getRangeTo(this) }!!.id

            return
        }

        if (hostileCreepsInRange.isNotEmpty()) {
            this.memory.targetId = hostileCreepsInRange.minBy { it.pos.getRangeTo(this) }!!.id

            return
        }

        if (otherHostileStructures.isNotEmpty()) {
            this.memory.targetId = otherHostileStructures.minBy { it.pos.getRangeTo(this) }!!.id

            return
        }

        moveTo(Game.flags["Rally"]!!)
    }
}

fun Creep.upgrade(assignedRoom: Room = this.room, controller: StructureController? = this.room.controller) {

    if (null == store.getCapacity(RESOURCE_ENERGY)) {
        return
    }

    if (null == controller) {
        return
    }

    if (memory.building && store[RESOURCE_ENERGY] == 0) {
        memory.building = false
        say("🔄 harvest")
    }
    if (!memory.building && store[RESOURCE_ENERGY]!! == store.getCapacity(RESOURCE_ENERGY)!!) {
        memory.building = true
        say("🚧 upgrade")
    }

    val currentRoomState = CurrentGameState.roomStates[assignedRoom.name]
            ?: throw RuntimeException("Missing current room status for ${assignedRoom.name}")

    if (memory.building) {
        if (upgradeController(controller) == ERR_NOT_IN_RANGE) {
            moveTo(
                    controller.pos,
                    options {
                        visualizePathStyle = options {
                            lineStyle = LINE_STYLE_DOTTED
                        }
                    }
            )
        }
    } else {
        val droppedSourcesInRange = currentRoomState.droppedEnergyResources.filter { it.pos.inRangeTo(this.pos, 4) }

        if (droppedSourcesInRange.isNotEmpty()) {
            when (pickup(droppedSourcesInRange.first())) {
                ERR_NOT_IN_RANGE -> moveTo(droppedSourcesInRange.first().pos)
                OK -> memory.building = true
            }

            return
        }

        val nonEmptyEnergyResourcesContainers = currentRoomState.energyContainers
                .filter { it.unsafeCast<StoreOwner>().store.getUsedCapacity(RESOURCE_ENERGY) > 0 }
                .sortedWith(WeightedStructureTypeComparator(mapOf<StructureConstant, Int>(STRUCTURE_STORAGE to 0, STRUCTURE_CONTAINER to 1)))

        if (nonEmptyEnergyResourcesContainers.isNotEmpty()) {
            when(withdraw(nonEmptyEnergyResourcesContainers.first().unsafeCast<StoreOwner>(), RESOURCE_ENERGY)){
                ERR_NOT_IN_RANGE -> moveTo(nonEmptyEnergyResourcesContainers.first().pos, options {
                    visualizePathStyle = options {
                        lineStyle = LINE_STYLE_DOTTED
                    }
                })
            }

            return
        }

        var activeSourcesInRange = this.pos.findInRange(FIND_SOURCES_ACTIVE, 1)

        if (activeSourcesInRange.isEmpty()) {
            activeSourcesInRange = assignedRoom.find(FIND_SOURCES_ACTIVE, options { filter = { it.pos.getSteppableAdjacent(true).isNotEmpty() } })
        }

        if (activeSourcesInRange.isNotEmpty()) {
            when (harvest(activeSourcesInRange[0])) {
                ERR_NOT_IN_RANGE -> moveTo(activeSourcesInRange[0].pos, options {
                    visualizePathStyle = options {
                        lineStyle = LINE_STYLE_DOTTED
                    }
                })
            }

            return
        }
    }
}

fun Creep.pause() {
    if (memory.pause < 10) {
        //blink slowly
        if (memory.pause % 3 != 0) say("\uD83D\uDEAC")
        memory.pause++
    } else {
        memory.pause = 0
        memory.role = Role.HARVESTER
    }
}

fun Creep.build(assignedRoom: Room = this.room) {

    if (null == store.getCapacity(RESOURCE_ENERGY))
        return

    if (memory.building && store[RESOURCE_ENERGY] == 0) {
        memory.building = false
        say("🔄 harvest")
    }
    if (!memory.building && store[RESOURCE_ENERGY]!! == store.getCapacity(RESOURCE_ENERGY)!!) {
        memory.building = true
        say("🚧 build")
    }

    val currentRoomState = CurrentGameState.roomStates[assignedRoom.name]
            ?: throw RuntimeException("Missing current room status for ${assignedRoom.name}")

    var constructionSites = currentRoomState.constructionSites
        .sortedBy { it.pos.getRangeTo(this.pos) }

    val spawnsFirst = currentRoomState.constructionSites.filter { it.structureType == STRUCTURE_SPAWN || it.structureType == STRUCTURE_EXTENSION }
    if (spawnsFirst.isNotEmpty()) {
        constructionSites = spawnsFirst
    }

    if (constructionSites.isEmpty()) {
        //todo deposit energy
        suicide()
    }

    if (memory.building) {
        if (constructionSites.isNotEmpty()) {
            if(build(constructionSites[0]) == ERR_NOT_IN_RANGE) {
                moveTo(constructionSites[0].pos, options {
                    reusePath = 1
                    visualizePathStyle = options {
                        lineStyle = LINE_STYLE_DOTTED
                    }
                })
            }

            return
        }
    } else {

        val containers = currentRoomState.energyContainers.filter { it.unsafeCast<StoreOwner>().store.getUsedCapacity(RESOURCE_ENERGY) > 0 }
                .sortedWith(WeightedStructureTypeComparator(mapOf<StructureConstant, Int>(STRUCTURE_STORAGE to 0, STRUCTURE_CONTAINER to 1)))

        if (containers.isNotEmpty()) {
            when (withdraw(containers[0].unsafeCast<StoreOwner>(), RESOURCE_ENERGY)) {
                ERR_NOT_IN_RANGE -> { moveTo(containers[0].pos, options {
                    visualizePathStyle = options {
                        lineStyle = LINE_STYLE_DOTTED
                    }
                }) }
            }

            return
        }

        //todo extract `find and harvest` logic
        var activeSourcesInRange = this.pos.findInRange(FIND_SOURCES_ACTIVE, 1)

        if (activeSourcesInRange.isEmpty()) {
            activeSourcesInRange = assignedRoom.find(FIND_SOURCES_ACTIVE, options { filter = { it.pos.getSteppableAdjacent(true).isNotEmpty() } })
        }

        if (activeSourcesInRange.isNotEmpty()) {
            when (harvest(activeSourcesInRange[0])) {
                ERR_NOT_IN_RANGE -> moveTo(activeSourcesInRange[0].pos, options {
                    visualizePathStyle = options {
                        lineStyle = LINE_STYLE_DOTTED
                    }
                })
            }

            return
        }

        if(this.memory.fallbackRoom != null)
        {
            val fallbackRoomState = CurrentGameState.roomStates[this.memory.fallbackRoom as String]
                    ?: throw RuntimeException("Missing current room status for ${assignedRoom.name}")

            val containers = fallbackRoomState.energyContainers.filter { it.unsafeCast<StoreOwner>().store.getUsedCapacity(RESOURCE_ENERGY) > 0 }
                    .sortedWith(WeightedStructureTypeComparator(mapOf<StructureConstant, Int>(STRUCTURE_STORAGE to 0, STRUCTURE_CONTAINER to 1)))

            if (containers.isNotEmpty()) {
                when (withdraw(containers[0].unsafeCast<StoreOwner>(), RESOURCE_ENERGY)) {
                    ERR_NOT_IN_RANGE -> { moveTo(containers[0].pos, options {
                        visualizePathStyle = options {
                            lineStyle = LINE_STYLE_DOTTED
                        }
                    }) }
                }

                return
            }
        }

    }
}

fun Creep.harvest(fromRoom: Room = this.room, toRoom: Room = this.room) {
    if (null == store.getCapacity(RESOURCE_ENERGY))
        return

    if (memory.building && store[RESOURCE_ENERGY] == 0) {
        memory.building = false
        say("harvest")
    }
    if (!memory.building && store[RESOURCE_ENERGY]!! == store.getCapacity(RESOURCE_ENERGY)!!) {
        memory.building = true
        say("storing")
    }

    val currentFromRoomState = CurrentGameState.roomStates[fromRoom.name]
            ?: throw RuntimeException("Missing current room status for ${fromRoom.name}")

    val currentToRoomState = CurrentGameState.roomStates[toRoom.name]
            ?: throw RuntimeException("Missing current room status for ${toRoom.name}")

    if (this.needsRenewing(currentToRoomState, 200)) {
        return
    }

    if (!memory.building) {
        //harvest
        val droppedSourcesInRange = currentFromRoomState.droppedEnergyResources.filter { it.pos.inRangeTo(pos,5) }

        if (droppedSourcesInRange.isNotEmpty()) {
            moveTo(droppedSourcesInRange.first().pos)
            pickup(droppedSourcesInRange.first())

            return
        }

        //todo extract `find and harvest` logic
        var activeSourcesInRange = currentFromRoomState.activeEnergySources.filter { it.pos.isNearTo(this) }.sortedBy { it.energy }

        if (activeSourcesInRange.isEmpty()) {
            activeSourcesInRange = currentFromRoomState.activeEnergySources.filter { it.pos.getSteppableAdjacent(true).isNotEmpty() }.sortedBy { it.energy }
        }

        if (activeSourcesInRange.isNotEmpty()) {
            when (harvest(activeSourcesInRange[0])) {
                ERR_NOT_IN_RANGE -> moveTo(activeSourcesInRange[0].pos)
            }
        }
    } else {
        //store
        var energyContainers = currentToRoomState.energyContainers.filter { it.unsafeCast<StoreOwner>().store.getFreeCapacity(RESOURCE_ENERGY) > 0 }

        if (this.memory.harvestAndDeliver) {
            val spawnsAndExtensions = currentToRoomState.myStructures.filter { it.isSpawnEnergyContainer() && it.unsafeCast<StoreOwner>().store.getFreeCapacity(RESOURCE_ENERGY) > 0 }
            if (spawnsAndExtensions.isNotEmpty()) {
                energyContainers = spawnsAndExtensions
            }
        }

        energyContainers = energyContainers.toMutableList().sortedBy { it.pos.getRangeTo(this.pos) }

        if (energyContainers.isNotEmpty()) {
            when (transfer(energyContainers.first().unsafeCast<StoreOwner>(), RESOURCE_ENERGY)) {
                ERR_NOT_IN_RANGE -> moveTo(energyContainers.first().pos)
            }
        } else {
            park(currentToRoomState)
        }
    }
}

fun Creep.repair(fromRoom: Room = this.room, toRoom: Room = this.room) {

    if (null == store.getCapacity(RESOURCE_ENERGY))
        return

    if (memory.building && store[RESOURCE_ENERGY] == 0) {
        memory.building = false
        say("harvest")
    }
    if (!memory.building && store[RESOURCE_ENERGY]!! == store.getCapacity(RESOURCE_ENERGY)!!) {
        memory.building = true
        this.memory.targetId = null //force acquiring new target
        say("repair")
    }

    val currentFromRoomState = CurrentGameState.roomStates[fromRoom.name]
            ?: throw RuntimeException("Missing current room status for ${fromRoom.name}")

    val currentToRoomState = CurrentGameState.roomStates[toRoom.name]
            ?: throw RuntimeException("Missing current room status for ${toRoom.name}")


    if (memory.building) {
        //repair
        if (null != this.memory.targetId) {
            val target = Game.getObjectById<Structure>(this.memory.targetId)
            if (null != target && target.hits < target.hitsMax) {
                if (repair(target) == ERR_NOT_IN_RANGE) {
                    moveTo(
                            target.pos,
                            options {
                                visualizePathStyle = screeps.api.options {
                                    lineStyle = screeps.api.LINE_STYLE_DOTTED
                                }
                            }
                    )
                }

                return
            } else {
                this.memory.targetId = null
            }
        }

        val damagedStructures = currentToRoomState.damagedStructures
        var targets = listOf<Structure>()

        val otherDamagedStructures = damagedStructures.filter { !it.isStructureTypeOf(STRUCTURE_WALL) }

        if (otherDamagedStructures.isNotEmpty()) {
            targets = otherDamagedStructures
        } else {
            var damagedWalls = damagedStructures.filter {
                it.isStructureTypeOf(STRUCTURE_WALL)
            }

            damagedWalls = damagedWalls.filterOrReturnExistingIfEmpty { it.isHpBelowPercent(1) }
            damagedWalls = damagedWalls.filterOrReturnExistingIfEmpty { it.isHpBelow(1500000) }
            damagedWalls = damagedWalls.filterOrReturnExistingIfEmpty { it.isHpBelow(1000000) }
            damagedWalls = damagedWalls.filterOrReturnExistingIfEmpty { it.isHpBelow(900000) }
            damagedWalls = damagedWalls.filterOrReturnExistingIfEmpty { it.isHpBelow(800000) }
            damagedWalls = damagedWalls.filterOrReturnExistingIfEmpty { it.isHpBelow(700000) }
            damagedWalls = damagedWalls.filterOrReturnExistingIfEmpty { it.isHpBelow(600000) }
            damagedWalls = damagedWalls.filterOrReturnExistingIfEmpty { it.isHpBelow(500000) }
            damagedWalls = damagedWalls.filterOrReturnExistingIfEmpty { it.isHpBelow(400000) }
            damagedWalls = damagedWalls.filterOrReturnExistingIfEmpty { it.isHpBelow(300000) }
            damagedWalls = damagedWalls.filterOrReturnExistingIfEmpty { it.isHpBelow(200000) }
            damagedWalls = damagedWalls.filterOrReturnExistingIfEmpty { it.isHpBelow(100000) }
            damagedWalls = damagedWalls.filterOrReturnExistingIfEmpty { it.isHpBelow(50000) }
            damagedWalls = damagedWalls.filterOrReturnExistingIfEmpty { it.isHpBelow(25000) }
            damagedWalls = damagedWalls.filterOrReturnExistingIfEmpty { it.isHpBelow(10000) }
            damagedWalls = damagedWalls.filterOrReturnExistingIfEmpty { it.isHpBelow(1000) }

            if (damagedWalls.isNotEmpty())
                targets = damagedWalls
        }

        targets = targets.toMutableList().sortedBy { it.hits + it.pos.getRangeTo(this.pos) }

        if (targets.isNotEmpty()) {
            this.memory.targetId = targets.first().id
            this.repair()
        } else {
            park(currentToRoomState)
        }
    } else {
        //replenish energy
        val droppedSourcesInRange = currentToRoomState.droppedEnergyResources.filter { it.pos.inRangeTo(this.pos, 4) }

        if (droppedSourcesInRange.isNotEmpty()) {
            when (pickup(droppedSourcesInRange.first())) {
                ERR_NOT_IN_RANGE -> moveTo(droppedSourcesInRange.first().pos)
                OK -> memory.building = true
            }

            return
        }

        val nonEmptyEnergyResourcesContainers = currentFromRoomState.energyContainers
                .filter { it.unsafeCast<StoreOwner>().store.getUsedCapacity(RESOURCE_ENERGY) > 0 }
                .sortedWith(WeightedStructureTypeComparator(mapOf<StructureConstant, Int>(STRUCTURE_STORAGE to 0, STRUCTURE_CONTAINER to 1)))

        if (nonEmptyEnergyResourcesContainers.isNotEmpty()) {
            when(withdraw(nonEmptyEnergyResourcesContainers.first().unsafeCast<StoreOwner>(), RESOURCE_ENERGY)){
                ERR_NOT_IN_RANGE -> moveTo(nonEmptyEnergyResourcesContainers.first().pos, options {
                    visualizePathStyle = options {
                        lineStyle = LINE_STYLE_DOTTED
                    }
                })
            }
        } else {
            park(currentFromRoomState)
        }
    }
}

fun Creep.park(currentRoomState: CurrentRoomState) {
    val parkX = currentRoomState.room.memory.parkX
    val parkY = currentRoomState.room.memory.parkY

    if (this.pos.x != parkX || this.pos.y != parkY) {
        when(moveTo(RoomPosition(parkX, parkY, currentRoomState.roomName), options { visualizePathStyle = options { lineStyle = LINE_STYLE_DOTTED } })) {
            ERR_NO_PATH -> console.log("$name no path available to ($parkX, $parkY)")
            ERR_NOT_FOUND -> console.log("$name no memorized path to use to ($parkX, $parkY)")
            ERR_INVALID_TARGET -> console.log("$name invalid target ($parkX, $parkY) [${JSON.stringify(currentRoomState.room.lookAt(parkX, parkY).first())}]")
            OK, ERR_NOT_OWNER, ERR_BUSY, ERR_TIRED, ERR_NO_BODYPART -> {}
            else -> {}
        }
    }
}

fun Creep.truck(assignedRoom: Room = this.room) {
    if (null == store.getCapacity(RESOURCE_ENERGY))
        return

    if (memory.building && store.getUsedCapacity(RESOURCE_ENERGY) == 0) {
        memory.building = false
        this.clearTargetId() //force acquiring new target
        say("🔄search and load")
    }
    if (!memory.building && store.getFreeCapacity(RESOURCE_ENERGY) == 0) {
        memory.building = true
        this.clearTargetId() //force acquiring new target
        say("🚧storing")
    }

    val currentRoomState = CurrentGameState.roomStates[assignedRoom.name]
            ?: throw RuntimeException("Missing current room status for ${assignedRoom.name}")

    if (this.needsRenewing(currentRoomState, 150)) {
        return
    }

    if (memory.building) {
        //storing
        if (null != this.getTargetId()) {
            val target = Game.getObjectById<StoreOwner>(this.getTargetId())

            if (null != target && target.store.getFreeCapacity(RESOURCE_ENERGY) > 0) {
                when (transfer(target, RESOURCE_ENERGY)) {
                    OK -> {
                    }
                    ERR_NOT_IN_RANGE -> {
                        moveTo(target.pos, options {
                            visualizePathStyle = screeps.api.options {
                                lineStyle = screeps.api.LINE_STYLE_DOTTED
                            }
                        })
                    }
                    ERR_FULL -> {
                       this.clearTargetId(); this.truck()
                    }
                    ERR_INVALID_TARGET -> {
                        console.log("Invalid target (${target.id}). Clearing"); this.clearTargetId()
                    }
                }

                return
            } else {
                this.clearTargetId()
            }
        }

        var energyContainers = currentRoomState.energyContainers.filter { it.isStructureTypeOf(STRUCTURE_STORAGE) && it.unsafeCast<StoreOwner>().store.getFreeCapacity(RESOURCE_ENERGY) > 0 }
        val myStructures = currentRoomState.myStructures
        val towers = myStructures.filter { it.isStructureTypeOf(STRUCTURE_TOWER) && it.unsafeCast<StoreOwner>().store.getFreeCapacity(RESOURCE_ENERGY) > 200  }
        val spawnsAndExtensions = myStructures.filter { it.isSpawnEnergyContainer() && it.unsafeCast<StoreOwner>().store.getFreeCapacity(RESOURCE_ENERGY) > 0 && !isIdTargetedByCreeps(it.id)  }

        if (spawnsAndExtensions.isNotEmpty()) {
            energyContainers = spawnsAndExtensions
        }

        if (towers.isNotEmpty()) {
            energyContainers = towers
        }

        energyContainers = energyContainers.toMutableList().sortedBy { it.pos.getRangeTo(this.pos) }

        if (energyContainers.isNotEmpty()) {
            this.setTargetId(energyContainers.first().id)
            this.truck()
            return
        }

        park(currentRoomState)
    } else {
        //search and load
        if (null != this.getTargetId()) {
            val target = Game.getObjectById<StoreOwner>(this.getTargetId())

            if (null != target && target.store.getUsedCapacity(RESOURCE_ENERGY) > 0) {
                when (withdraw(target, RESOURCE_ENERGY)) {
                    OK -> {
                    }
                    ERR_NOT_IN_RANGE -> {
                        moveTo(target.pos, options {
                            visualizePathStyle = screeps.api.options {
                                lineStyle = screeps.api.LINE_STYLE_DOTTED
                            }
                        })
                    }
                    ERR_FULL -> {
                        console.log("Belly full of ${this.store.keys}. Storing."); this.clearTargetId();
                    }
                    ERR_INVALID_TARGET -> {
                        console.log("Invalid target (${target.id}). Clearing"); this.clearTargetId()
                    }
                }
                return
            } else {
                this.clearTargetId()
            }
        }
        val creepCargoCapacity = this.store.getCapacity()!!

        val tombstones = currentRoomState.tombstones.filter {
            it.pos.inRangeTo(pos, 30) &&
                    it.store.getUsedCapacity(RESOURCE_ENERGY) >= creepCargoCapacity &&
                    !isIdTargetedByCreeps(it.id)
        }

        if (tombstones.isNotEmpty()) {
            this.setTargetId(tombstones.first().id)
            this.truck()
            return
        }

        // include storage when room's construction energy levels are lower then max
        val nonEmptyEnergyContainerStructures = if (currentRoomState.room.energyAvailable < currentRoomState.room.energyCapacityAvailable) {
            currentRoomState.energyContainers.filter { it.unsafeCast<StoreOwner>().store.getUsedCapacity(RESOURCE_ENERGY) > 0 }
                    .sortedBy { it.pos.getRangeTo(pos) }
                    .sortedWith(WeightedStructureTypeComparator(mapOf<StructureConstant, Int>(STRUCTURE_STORAGE to 0, STRUCTURE_CONTAINER to 1)))

        } else {
            currentRoomState.energyContainers.filter { it.isStructureTypeOf(STRUCTURE_CONTAINER) && it.unsafeCast<StoreOwner>().store.getUsedCapacity(RESOURCE_ENERGY) > creepCargoCapacity  }.sortedBy { it.pos.getRangeTo(pos) }
        }

        if (nonEmptyEnergyContainerStructures.isNotEmpty()) {
            this.setTargetId(nonEmptyEnergyContainerStructures.first().id)
            this.truck()
            return
        }

        if (this.store.getUsedCapacity() > 0) {
            this.memory.building = !this.memory.building
        } else {
            park(currentRoomState)
        }
    }
}

private fun Creep.needsRenewing(currentRoomState: CurrentRoomState, minimumTicksToLive: Int = 50): Boolean {
    if (currentRoomState.room.energyAvailable < SPAWN_ENERGY_CAPACITY) {
        this.memory.renewing = false

        return false
    }

    if (this.memory.renewing || this.ticksToLive < minimumTicksToLive) {
        this.memory.renewing = true

        val spawn = currentRoomState.myStructures.firstOrNull { it.isStructureTypeOf(STRUCTURE_SPAWN) }
        if (spawn != null) {
            when (val returnCode = spawn.unsafeCast<StructureSpawn>().renewCreep(this)) {
                OK -> {}
                ERR_NOT_ENOUGH_ENERGY -> { console.log("Waiting for the spawn to get more energy"); return false}
                ERR_BUSY -> { console.log("Waiting for the spawn to finish work"); return false }
                ERR_NOT_IN_RANGE -> this.moveTo(spawn)
                ERR_FULL -> {
                    this.memory.renewing = false;
                    console.log("Creep renewed. Resuming work.");
                }
                else -> console.log("Unhandled error code $returnCode")
            }
        } else {
            this.memory.renewing = false;
        }
    }

    return this.memory.renewing
}