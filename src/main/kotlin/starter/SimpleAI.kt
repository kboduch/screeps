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

    when (true) {
        spawnBigHarvesters(Game.creeps.values, mainSpawn) -> {}
        spawnCreeps(arrayOf(WORK, WORK, WORK, CARRY, CARRY, MOVE, MOVE, MOVE, MOVE, MOVE), Game.creeps.values, mainSpawn) -> {}
        spawnCreeps(arrayOf(WORK, WORK, WORK, CARRY, MOVE, MOVE, MOVE, MOVE), Game.creeps.values, mainSpawn) -> {}
        spawnCreeps(arrayOf(WORK, CARRY, MOVE, MOVE), Game.creeps.values, mainSpawn) -> {}
    }

    // build a few extensions so we can have 550 energy
    val controller = mainSpawn.room.controller
    if (controller != null && controller.level >= 2) {
        when (controller.room.find(FIND_MY_STRUCTURES).count { it.isStructureTypeOf(STRUCTURE_EXTENSION) }) {
            0 -> controller.room.createConstructionSite(29, 27, STRUCTURE_EXTENSION)
            1 -> controller.room.createConstructionSite(28, 27, STRUCTURE_EXTENSION)
            2 -> controller.room.createConstructionSite(27, 27, STRUCTURE_EXTENSION)
            3 -> controller.room.createConstructionSite(26, 27, STRUCTURE_EXTENSION)
            4 -> controller.room.createConstructionSite(25, 27, STRUCTURE_EXTENSION)
            5 -> controller.room.createConstructionSite(24, 27, STRUCTURE_EXTENSION)
            6 -> controller.room.createConstructionSite(23, 27, STRUCTURE_EXTENSION)
        }
    }

    val containers = mainSpawn.room.find(FIND_STRUCTURES).filter { it.isEnergyContainer() }
    mainSpawn.room.visual.text(
            "${Game.time} ${mainSpawn.room.energyAvailable}/${mainSpawn.room.energyCapacityAvailable} ${containers.sumBy { it.unsafeCast<StoreOwner>().store[RESOURCE_ENERGY]!! }}/${containers.sumBy { it.unsafeCast<StoreOwner>().store.getCapacity(RESOURCE_ENERGY)!! }}",
            0.0,
            0.0,
            options { align = TEXT_ALIGN_LEFT }
    )

    for ((_, creep) in Game.creeps) {
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
            STRUCTURE_TOWER -> towerAction(structure as StructureTower)
        }
    }
    test(mainSpawn)
}

private fun test(spawn: StructureSpawn) {

}

private fun towerAction(tower: StructureTower) {
    val hostileRange = 10
    val friendlyRange = 20


    tower.room.visual
            .circle(tower.pos, options {
                radius = hostileRange.toDouble()
                fill = "transparent"
                stroke = "red"
                lineStyle = LINE_STYLE_DOTTED
                opacity = 0.15
            })
            .circle(tower.pos, options {
                radius = friendlyRange.toDouble()
                fill = "transparent"
                stroke = "green"
                lineStyle = LINE_STYLE_DOTTED
                opacity = 0.15
            })

    val hostileCreeps = tower.pos.findInRange(FIND_HOSTILE_CREEPS, hostileRange)
    if (hostileCreeps.isNotEmpty()) {
        Game.notify("Spotted enemy creep ${hostileCreeps[0].owner.username}")
        tower.attack(hostileCreeps[0])

        return
    }

    val damagedRoads = tower.pos.findInRange(FIND_STRUCTURES, friendlyRange, options { filter = { it.isStructureTypeOf(STRUCTURE_ROAD) && it.isHpBelowPercent(100) } })
    if (damagedRoads.isNotEmpty()) {
        tower.repair(damagedRoads[0])

        return
    }
}

//Refactor this method
private fun spawnBigHarvesters(
        creeps: Array<Creep>,
        spawn: StructureSpawn
): Boolean {
    val role: Role = when {
        creeps.count { it.memory.role == Role.HARVESTER } < 4 -> Role.HARVESTER
        else -> return false
    }

    val body = arrayOf<BodyPartConstant>(
            WORK, WORK, WORK, WORK,
            CARRY, CARRY, CARRY, CARRY, CARRY, CARRY, CARRY,
            MOVE, MOVE, MOVE, MOVE, MOVE, MOVE, MOVE, MOVE, MOVE, MOVE, MOVE
    )

    val bodyPartsCost = body.sumBy { BODYPART_COST[it]!! }
    if (spawn.room.energyAvailable < bodyPartsCost) {
        return false
    }

    val newName = "${role.name}_${bodyPartsCost}_${Game.time}"
    val code = spawn.spawnCreep(body, newName, options {
        memory = jsObject<CreepMemory> { this.role = role }
        energyStructures = determineSpawnEnergyStructures(spawn) as Array<StoreOwner>
    })

    return when (code) {
        OK -> {console.log("Spawning \"$newName\" with body \'$body\' cost: $bodyPartsCost") ; true }
        ERR_BUSY, ERR_NOT_ENOUGH_ENERGY -> false
        else -> { console.log("unhandled error code $code"); false }
    }
}

private fun spawnCreeps(
        body: Array<BodyPartConstant>,
        creeps: Array<Creep>,
        spawn: StructureSpawn
) : Boolean {
    val bodyPartsCost = body.sumBy { BODYPART_COST[it]!! }
    if (spawn.room.energyAvailable < bodyPartsCost) {
        return false
    }

    val damagedStructures = mutableListOf<Structure>()

    damagedStructures.addAll(
            spawn.room.find(FIND_STRUCTURES, options {
                filter = {
                    (80 > (100 * it.hits / it.hitsMax)) &&
                            !it.isStructureTypeOf(arrayOf<StructureConstant>(STRUCTURE_CONTROLLER, STRUCTURE_WALL))
                }
            })
    )

    damagedStructures.addAll(
            spawn.room.find(FIND_STRUCTURES, options {
                filter = {
                            500000 > it.hits &&
                            it.isStructureTypeOf(STRUCTURE_WALL)
                }
            })
    )

    var minimumUpgraders = spawn.room.controller?.level ?: 0

    val containers = spawn.room.find(FIND_STRUCTURES)
            .filter { it.isEnergyContainer() }

    if (
            spawn.room.energyAvailable == spawn.room.energyCapacityAvailable &&
            containers.isNotEmpty() && containers.sumBy { it.unsafeCast<StoreOwner>().store[RESOURCE_ENERGY]!! } == containers.sumBy { it.unsafeCast<StoreOwner>().store.getCapacity(RESOURCE_ENERGY)!! }
    ) {
        minimumUpgraders += 2
    }

    val role: Role = when {

        creeps.count { it.memory.role == Role.HARVESTER } < 4 -> Role.HARVESTER

        creeps.count { it.memory.role == Role.UPGRADER } < minimumUpgraders -> Role.UPGRADER

        spawn.room.find(FIND_MY_CONSTRUCTION_SITES).isNotEmpty() &&
                creeps.count { it.memory.role == Role.BUILDER } < 3 -> Role.BUILDER

        damagedStructures.isNotEmpty() && creeps.count { it.memory.role == Role.REPAIRER } < 2 -> Role.REPAIRER

        else -> return false
    }

    val newName = "${role.name}_${bodyPartsCost}_${Game.time}"
    val code = spawn.spawnCreep(body, newName, options {
        memory = jsObject<CreepMemory> { this.role = role }
        energyStructures = determineSpawnEnergyStructures(spawn) as Array<StoreOwner>
    })

    return when (code) {
        OK -> {console.log("Spawning \"$newName\" with body \'$body\' cost: $bodyPartsCost") ; true }
        ERR_BUSY, ERR_NOT_ENOUGH_ENERGY -> false
        else -> { console.log("unhandled error code $code"); false }
    }
}

private fun houseKeeping(creeps: Record<String, Creep>) {
    if (Game.creeps.isEmpty()) return  // this is needed because Memory.creeps is undefined

    for ((creepName, _) in Memory.creeps) {
        if (creeps[creepName] == null) {
            console.log("Deleting obsolete memory entry for creep $creepName")
            delete(Memory.creeps[creepName])
        }
    }
}

private fun determineSpawnEnergyStructures(spawn: StructureSpawn): Array<Structure> {
    val availableEnergyStructures = spawn.room.find(
            FIND_MY_STRUCTURES,
            options { filter = { it.isStructureTypeOf(arrayOf<StructureConstant>(STRUCTURE_EXTENSION, STRUCTURE_SPAWN)) } }
    )

    when (spawn.memory.energyStructuresDirFromToOpposite) {
        TOP_LEFT, TOP, TOP_RIGHT -> availableEnergyStructures.sortBy { it.pos.y }
        RIGHT -> availableEnergyStructures.sortByDescending { it.pos.x }
        BOTTOM_RIGHT, BOTTOM, BOTTOM_LEFT -> availableEnergyStructures.sortByDescending { it.pos.y }
        LEFT -> availableEnergyStructures.sortBy { it.pos.x }
    }

    return availableEnergyStructures
}
