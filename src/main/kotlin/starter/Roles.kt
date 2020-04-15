package starter

import screeps.api.*
import screeps.api.structures.Structure
import screeps.api.structures.StructureController


enum class Role {
    UNASSIGNED,
    HARVESTER,
    BUILDER,
    UPGRADER,
    REPAIRER
}

fun Creep.upgrade(fromRoom: Room = this.room, controller: StructureController) {

    if (null == store.getCapacity(RESOURCE_ENERGY))
        return

    if (memory.building && store[RESOURCE_ENERGY] == 0) {
        memory.building = false
        say("🔄 harvest")
    }
    if (!memory.building && store[RESOURCE_ENERGY]!! == store.getCapacity(RESOURCE_ENERGY)!!) {
        memory.building = true
        say("🚧 upgrade")
    }

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
        val droppedSourcesInRange = fromRoom.find(FIND_DROPPED_RESOURCES, options { filter = { it.resourceType == RESOURCE_ENERGY } })
                .filter { it.pos.inRangeTo(pos,4) }

        if (droppedSourcesInRange.isNotEmpty()) {
            when (pickup(droppedSourcesInRange.first())) {
                ERR_NOT_IN_RANGE -> moveTo(droppedSourcesInRange.first().pos)
                OK -> memory.building = true
            }

            return
        }

        val targets = fromRoom.find(FIND_STRUCTURES)
                .filter { it.isEnergyContainer() }
                .filter { 0 < it.unsafeCast<StoreOwner>().store[RESOURCE_ENERGY]!! }

        if (targets.isNotEmpty()) {
            if (withdraw(targets[0] as StoreOwner, RESOURCE_ENERGY) == ERR_NOT_IN_RANGE) {
                moveTo(targets[0].pos)
            }
        } else {
            moveTo(Game.flags["park"]?.pos?.x!!, Game.flags["park"]?.pos?.y!!)
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

    val constructionSites = assignedRoom.find(FIND_MY_CONSTRUCTION_SITES)

    if (constructionSites.isEmpty()) {
        //todo deposit energy
        suicide()
    }

    if (memory.building) {
        if (constructionSites.isNotEmpty()) {
            if (build(constructionSites[0]) == ERR_NOT_IN_RANGE) {
                moveTo(constructionSites[0].pos)
            }
            return
        }
    } else {

        val containers = assignedRoom.find(FIND_STRUCTURES).filter { it.isEnergyContainer() && it.unsafeCast<StoreOwner>().store.getUsedCapacity(RESOURCE_ENERGY) > 0 }

        if (containers.isNotEmpty()) {
            if (withdraw(containers[0] as StoreOwner, RESOURCE_ENERGY) == ERR_NOT_IN_RANGE) {
                moveTo(containers[0].pos)
            }
            return
        }

        //todo extract `find and harvest` logic
        var activeSourcesInRange = this.pos.findInRange(FIND_SOURCES_ACTIVE, 1)

        if (activeSourcesInRange.isEmpty()) {
            activeSourcesInRange = assignedRoom.find(FIND_SOURCES_ACTIVE, options { filter = { it.pos.getSteppableAdjacent(true).isNotEmpty() } })
        }

        if (activeSourcesInRange.isNotEmpty())
            if (harvest(activeSourcesInRange[0]) == ERR_NOT_IN_RANGE) {
                moveTo(activeSourcesInRange[0].pos)
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

    if (!memory.building) {

        if (body.sumBy { BODYPART_COST[it.type]!! } < 1000 && toRoom.energyAvailable < toRoom.energyCapacityAvailable) {
            val containers = toRoom.find(FIND_STRUCTURES)
                    .filter { it.isEnergyContainer() }
                    .filter { 0 < it.unsafeCast<StoreOwner>().store[RESOURCE_ENERGY]!!}
            if (containers.isNotEmpty()) {
                if (withdraw(containers[0] as StoreOwner, RESOURCE_ENERGY) == ERR_NOT_IN_RANGE) {
                    moveTo(containers[0].pos)
                }

                return
            }
        }

        val droppedSourcesInRange = fromRoom.find(FIND_DROPPED_RESOURCES, options { filter = { it.resourceType == RESOURCE_ENERGY } })
                .filter { it.pos.inRangeTo(pos,5) }

        if (droppedSourcesInRange.isNotEmpty()) {
            if (pickup(droppedSourcesInRange.first()) == ERR_NOT_IN_RANGE) {
                moveTo(droppedSourcesInRange.first().pos)
            }

            return
        }

        //todo extract `find and harvest` logic
        var activeSourcesInRange = this.pos.findInRange(FIND_SOURCES_ACTIVE, 1)

        if (activeSourcesInRange.isEmpty()) {
            activeSourcesInRange = fromRoom.find(FIND_SOURCES_ACTIVE, options { filter = { it.pos.getSteppableAdjacent(true).isNotEmpty() } })
        }

        if (activeSourcesInRange.isNotEmpty())
            if (harvest(activeSourcesInRange[0]) == ERR_NOT_IN_RANGE) {
                moveTo(activeSourcesInRange[0].pos)
            }
    } else {

        var energyContainers = room.find(FIND_STRUCTURES).filter { it.isEnergyContainer() && it.unsafeCast<StoreOwner>().store.getFreeCapacity(RESOURCE_ENERGY) > 0 }
        val myStructures = room.find(FIND_MY_STRUCTURES)
        val towers = myStructures.filter { it.structureType == STRUCTURE_TOWER && it.unsafeCast<StoreOwner>().store[RESOURCE_ENERGY] < TOWER_CAPACITY  }
        val spawn = myStructures.filter { it.isSpawnEnergyContainer() && it.unsafeCast<StoreOwner>().store.getFreeCapacity(RESOURCE_ENERGY) > 0  }

        if (towers.isNotEmpty()) {
            energyContainers = towers
        }

        if (spawn.isNotEmpty()) {
            energyContainers = spawn
        }

        energyContainers = energyContainers.toMutableList().sortedBy { it.pos.getRangeTo(this.pos) }

        if (energyContainers.isNotEmpty()) {
            if (transfer(energyContainers[0] as StoreOwner, RESOURCE_ENERGY) == ERR_NOT_IN_RANGE) {
                moveTo(energyContainers[0].pos)
            }
        } else {
            moveTo(Game.flags["park"]?.pos?.x!!, Game.flags["park"]?.pos?.y!!)
        }
    }
}

fun Creep.repair(fromRoom: Room = this.room, toRoom: Room = this.room) {

    if (null == store.getCapacity(RESOURCE_ENERGY))
        return

    if (memory.building && store[RESOURCE_ENERGY] == 0) {
        memory.building = false
        say("🔄 harvest")
    }
    if (!memory.building && store[RESOURCE_ENERGY]!! == store.getCapacity(RESOURCE_ENERGY)!!) {
        memory.building = true
        say("🚧 repair")
    }


    if (memory.building) {

        val damagedStructures = toRoom.find(
                FIND_STRUCTURES,
                options { filter = { it.structureType != STRUCTURE_CONTROLLER && it.hits < it.hitsMax} }
        )

        var targets = listOf<Structure>()

        val otherDamagedStructures = damagedStructures.filter { it.structureType != STRUCTURE_WALL }

        if (otherDamagedStructures.isNotEmpty()) {
            targets = otherDamagedStructures
        } else {
            var damagedWalls = damagedStructures.filter {
                it.structureType == STRUCTURE_WALL
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
            if (repair(targets[0]) == ERR_NOT_IN_RANGE) {
                moveTo(
                        targets[0].pos,
                        options {
                            visualizePathStyle = options {
                                lineStyle = LINE_STYLE_DOTTED
                            }
                        }
                )
            }
        } else {
            moveTo(Game.flags["park"]?.pos?.x!!, Game.flags["park"]?.pos?.y!!)
        }
    } else {
        val targets = fromRoom.find(FIND_STRUCTURES)
                .filter { it.isEnergyContainer() }
                .filter { 0 < it.unsafeCast<StoreOwner>().store[RESOURCE_ENERGY]!! }

        if (targets.isNotEmpty()) {
            if (withdraw(targets[0] as StoreOwner, RESOURCE_ENERGY) == ERR_NOT_IN_RANGE) {
                moveTo(targets[0].pos)
            }
        } else {
            moveTo(Game.flags["park"]?.pos?.x!!, Game.flags["park"]?.pos?.y!!)
        }
    }
}
