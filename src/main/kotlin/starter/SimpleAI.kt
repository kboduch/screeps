package starter


import screeps.api.*
import screeps.api.structures.Structure
import screeps.api.structures.StructureSpawn
import screeps.api.structures.StructureTower
import screeps.utils.isEmpty
import screeps.utils.unsafe.delete
import screeps.utils.unsafe.jsObject

fun gameLoop() {
    val mainSpawn: StructureSpawn = Game.spawns.values.firstOrNull() ?: return

    //delete memories of creeps that have passed away
    houseKeeping(Game.creeps)

    // just an example of how to use room memory
    mainSpawn.room.memory.numberOfCreeps = mainSpawn.room.find(FIND_CREEPS).count()

    spawnBigHarvesters(Game.creeps.values, mainSpawn)
    //make sure we have at least some creeps
    spawnCreeps(arrayOf(WORK, WORK, WORK, WORK, CARRY, MOVE, MOVE), Game.creeps.values, mainSpawn)
    spawnCreeps(arrayOf(WORK, CARRY, MOVE), Game.creeps.values, mainSpawn)

    // build a few extensions so we can have 550 energy
    val controller = mainSpawn.room.controller
    if (controller != null && controller.level >= 2) {
        when (controller.room.find(FIND_MY_STRUCTURES).count { it.structureType == STRUCTURE_EXTENSION }) {
            0 -> controller.room.createConstructionSite(29, 27, STRUCTURE_EXTENSION)
            1 -> controller.room.createConstructionSite(28, 27, STRUCTURE_EXTENSION)
            2 -> controller.room.createConstructionSite(27, 27, STRUCTURE_EXTENSION)
            3 -> controller.room.createConstructionSite(26, 27, STRUCTURE_EXTENSION)
            4 -> controller.room.createConstructionSite(25, 27, STRUCTURE_EXTENSION)
            5 -> controller.room.createConstructionSite(24, 27, STRUCTURE_EXTENSION)
            6 -> controller.room.createConstructionSite(23, 27, STRUCTURE_EXTENSION)
        }
    }

    console.log("*====*")
    val containers = mainSpawn.room.find(FIND_STRUCTURES).filter { it.isEnergyContainer() }
    console.log(
            Game.time,
            "${mainSpawn.room.energyAvailable}/${mainSpawn.room.energyCapacityAvailable}",
            "${containers.sumBy { it.unsafeCast<StoreOwner>().store[RESOURCE_ENERGY]!! }}/${containers.sumBy { it.unsafeCast<StoreOwner>().store.getCapacity(RESOURCE_ENERGY)!! }}"
    )
    for ((_, creep) in Game.creeps) {
        if(Role.HARVESTER == creep.memory.role)
            console.log(creep.ticksToLive, creep.body.map { it.type })
        when (creep.memory.role) {
            Role.REPAIRER -> creep.repair()
            Role.HARVESTER -> creep.harvest()
            Role.BUILDER -> creep.build()
            Role.UPGRADER -> creep.upgrade(controller = mainSpawn.room.controller!!)
            else -> creep.pause()
        }
    }

    for ((_, structure) in Game.structures) {
        when (structure.structureType) {
            STRUCTURE_TOWER -> attack(structure as StructureTower)
        }
    }

}

private fun attack(tower: StructureTower) {
    val hostileCreeps = tower.pos.findInRange(FIND_HOSTILE_CREEPS,5)
    if (hostileCreeps.isNotEmpty()) {
        Game.notify("Spotted enemy creep ${hostileCreeps[0].owner.username}")
        tower.attack(hostileCreeps[0])
    }
}

//Refactor this method
private fun spawnBigHarvesters(
        creeps: Array<Creep>,
        spawn: StructureSpawn
) {
    val role: Role = when {
        creeps.count { it.memory.role == Role.HARVESTER } < 5 -> Role.HARVESTER
        else -> return
    }

    val body = arrayOf<BodyPartConstant>(
            WORK,
            WORK,
            WORK,
            WORK,
            CARRY,
            CARRY,
            CARRY,
            CARRY,
            CARRY,
            CARRY,
            CARRY,
            MOVE,
            MOVE,
            MOVE,
            MOVE,
            MOVE,
            MOVE,
            MOVE,
            MOVE,
            MOVE,
            MOVE,
            MOVE
    )

    if (spawn.room.energyAvailable < body.sumBy { BODYPART_COST[it]!! }) {
        return
    }

    val newName = "${role.name}_${Game.time}"
    val code = spawn.spawnCreep(body, newName, options {
        memory = jsObject<CreepMemory> { this.role = role }
    })

    when (code) {
        OK -> console.log("spawning $newName with body $body")
        ERR_BUSY, ERR_NOT_ENOUGH_ENERGY -> run { } // do nothing
        else -> console.log("unhandled error code $code")
    }
}

private fun spawnCreeps(
        body: Array<BodyPartConstant>,
        creeps: Array<Creep>,
        spawn: StructureSpawn
) {
    if (spawn.room.energyAvailable < body.sumBy { BODYPART_COST[it]!! }) {
        return
    }

    val damagedStructures = mutableListOf<Structure>()

    damagedStructures.addAll(
            spawn.room.find(FIND_STRUCTURES, options {
                filter = {
                    (80 > (100 * it.hits / it.hitsMax)) &&
                            it.structureType != STRUCTURE_CONTROLLER && it.structureType != STRUCTURE_WALL
                }
            })
    )

    damagedStructures.addAll(
            spawn.room.find(FIND_STRUCTURES, options {
                filter = {
                            500000 > it.hits &&
                            it.structureType == STRUCTURE_WALL
                }
            })
    )

    var minimumUpgraders = 3;

    val containers = spawn.room.find(FIND_STRUCTURES)
            .filter { it.structureType == STRUCTURE_CONTAINER }

    if (
            spawn.room.energyAvailable == spawn.room.energyCapacityAvailable &&
            containers.sumBy { it.unsafeCast<StoreOwner>().store[RESOURCE_ENERGY]!! } == containers.sumBy { it.unsafeCast<StoreOwner>().store.getCapacity(RESOURCE_ENERGY)!! }
    ) {
        minimumUpgraders = 5
    }

    val role: Role = when {

        creeps.count { it.memory.role == Role.HARVESTER } < 5 -> Role.HARVESTER

        creeps.count { it.memory.role == Role.UPGRADER } < minimumUpgraders -> Role.UPGRADER

        spawn.room.find(FIND_MY_STRUCTURES, options { filter = { it.structureType == STRUCTURE_TOWER && it.unsafeCast<StoreOwner>().store[RESOURCE_ENERGY] < TOWER_CAPACITY } }).isNotEmpty() &&
                creeps.count { it.memory.role == Role.BUILDER } < 1 -> Role.BUILDER

        spawn.room.find(FIND_MY_CONSTRUCTION_SITES).isNotEmpty() &&
                creeps.count { it.memory.role == Role.BUILDER } < 3 -> Role.BUILDER

        damagedStructures.isNotEmpty() && creeps.count { it.memory.role == Role.REPAIRER } < 1 -> Role.REPAIRER

        else -> return
    }

    val newName = "${role.name}_${Game.time}"
    val code = spawn.spawnCreep(body, newName, options {
        memory = jsObject<CreepMemory> { this.role = role }
    })

    when (code) {
        OK -> console.log("spawning $newName with body $body")
        ERR_BUSY, ERR_NOT_ENOUGH_ENERGY -> run { } // do nothing
        else -> console.log("unhandled error code $code")
    }
}

private fun houseKeeping(creeps: Record<String, Creep>) {
    if (Game.creeps.isEmpty()) return  // this is needed because Memory.creeps is undefined

    for ((creepName, _) in Memory.creeps) {
        if (creeps[creepName] == null) {
            console.log("deleting obsolete memory entry for creep $creepName")
            delete(Memory.creeps[creepName])
        }
    }
}
