package starter

import screeps.api.*
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

    val towers = assignedRoom.find(FIND_MY_STRUCTURES, options { filter = { it.structureType == STRUCTURE_TOWER && it.unsafeCast<StoreOwner>().store[RESOURCE_ENERGY] < TOWER_CAPACITY } })
    val constructionSites = assignedRoom.find(FIND_MY_CONSTRUCTION_SITES)

    if (towers.isEmpty() && constructionSites.isEmpty()) {
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

        if (towers.isNotEmpty()) {
            if (transfer(towers[0] as StoreOwner, RESOURCE_ENERGY) == ERR_NOT_IN_RANGE) {
                moveTo(towers[0].pos)
            }
        }
    } else {

        val containers = assignedRoom.find(FIND_STRUCTURES).filter { it.isEnergyContainer() }
        val enoughEnergyInContainers = containers.sumBy { it.unsafeCast<StoreOwner>().store[RESOURCE_ENERGY]!! } > (containers.sumBy { it.unsafeCast<StoreOwner>().store.getCapacity(RESOURCE_ENERGY)!! } / 2)

        if (towers.isNotEmpty() && enoughEnergyInContainers) {
            if (withdraw(containers[0] as StoreOwner, RESOURCE_ENERGY) == ERR_NOT_IN_RANGE) {
                moveTo(containers[0].pos)
            }
            return
        }

        val sources = room.find(FIND_SOURCES, options { filter = { it.energy > 0 } })
        if (sources.isNotEmpty()){}
            if (harvest(sources[0]) == ERR_NOT_IN_RANGE) {
                moveTo(sources[0].pos)
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
                .filter { it.pos.inRangeTo(pos,3) }

        if (droppedSourcesInRange.isNotEmpty()) {
            if (pickup(droppedSourcesInRange.first()) == ERR_NOT_IN_RANGE) {
                moveTo(droppedSourcesInRange.first().pos)
            }

            return
        }

        val sources = fromRoom.find(FIND_SOURCES, options { filter = { it.energy > 0 } })
        if (sources.isNotEmpty())
            if (harvest(sources[0]) == ERR_NOT_IN_RANGE) {
                moveTo(sources[0].pos)
            }
    } else {

        val baseTargets = toRoom.find(FIND_MY_STRUCTURES)
            .filter { (it.structureType == STRUCTURE_EXTENSION || it.structureType == STRUCTURE_SPAWN) }
            .filter { it.unsafeCast<StoreOwner>().store[RESOURCE_ENERGY]!! < it.unsafeCast<StoreOwner>().store.getCapacity(RESOURCE_ENERGY)!! }

        val targets = baseTargets.toMutableList()
        targets.addAll(
                toRoom.find(FIND_STRUCTURES)
                        .filter { it.isEnergyContainer() }
                        .filter { it.unsafeCast<StoreOwner>().store[RESOURCE_ENERGY]!! < it.unsafeCast<StoreOwner>().store.getCapacity(RESOURCE_ENERGY)!! }
        )

        if (targets.isNotEmpty()) {
            if (transfer(targets[0] as StoreOwner, RESOURCE_ENERGY) == ERR_NOT_IN_RANGE) {
                moveTo(targets[0].pos)
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

        val targets = toRoom.find(
                FIND_STRUCTURES,
                options {
                    filter = {
                        ((it.structureType != STRUCTURE_CONTROLLER && it.structureType != STRUCTURE_WALL) && (it.hits < it.hitsMax))
                                || ((it.structureType == STRUCTURE_WALL) && (500000 > it.hits))
                    }
                }
        )

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
