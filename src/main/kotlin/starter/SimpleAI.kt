package starter


import screeps.api.*
import screeps.api.structures.Structure
import screeps.api.structures.StructureSpawn
import screeps.api.structures.StructureTower
import screeps.utils.isEmpty
import screeps.utils.unsafe.delete
import screeps.utils.unsafe.jsObject

fun gameLoop() {
    //gather info
    //damaged structures //walls,ramparts,other structures
    //global wall hp level
    //available energy sources
    //empty containers
    // find what else
    //
    //store it globally for all creeps to be able to access, instead of recalculating the same thing over and over for each creep

    Game.rooms.values.forEach { room: Room ->
        if (room.controller != null && room.controller!!.my) {
            CurrentGameState.roomStates[room.name] = CurrentRoomState(room)
        }
    }

    Game.flags.values.forEach { flag ->
        if (flag.name == "Assault" && flag.memory.roomName != null ) {
            CurrentGameState.assaultTargetRoomName = flag.memory.roomName
        }
    }

    CurrentGameState.roomStates.forEach {(roomName, currentRoomState) ->
        currentRoomState.room.visual.text(
                "$roomName ${currentRoomState.room.energyAvailable}/${currentRoomState.room.energyCapacityAvailable} ${currentRoomState.energyContainersTotalLevel}/${currentRoomState.energyContainersTotalMaximumLevel}",
                0.0,
                0.0,
                options { align = screeps.api.TEXT_ALIGN_LEFT }
        )
    }

    val mainSpawn: StructureSpawn = Game.spawns.values.firstOrNull() ?: return

    //delete memories of creeps that have passed away
    houseKeeping(Game.creeps)

    when (true) {
        spawnBigHarvesters(Game.creeps.values, mainSpawn) -> {}
        CurrentGameState.assaultTargetRoomName != null && spawnAssaulter(Game.creeps.values, mainSpawn) -> {}
        spawnTrucker(Game.creeps.values, mainSpawn) -> {}
        spawnCreeps(arrayOf(WORK, WORK, WORK, CARRY, CARRY, MOVE, MOVE, MOVE, MOVE, MOVE), Game.creeps.values, mainSpawn) -> {}
        spawnCreeps(arrayOf(WORK, WORK, WORK, CARRY, MOVE, MOVE, MOVE, MOVE), Game.creeps.values, mainSpawn) -> {}
        spawnCreeps(arrayOf(WORK, CARRY, MOVE, MOVE), Game.creeps.values, mainSpawn) -> {}
    }

    //todo write a spawning logic
    // o is extension, x is road, S is spawn
    // x o x o x
    // o x S x o
    // x o x o x
    // o x o x o
    // build a few extensions so we can have 550 energy
//    val controller = mainSpawn.room.controller
//    if (controller != null && controller.level >= 2) {
//        when (controller.room.find(FIND_MY_STRUCTURES).count { it.isStructureTypeOf(STRUCTURE_EXTENSION) }) {
//            0 -> controller.room.createConstructionSite(29, 27, STRUCTURE_EXTENSION)
//            1 -> controller.room.createConstructionSite(28, 27, STRUCTURE_EXTENSION)
//            2 -> controller.room.createConstructionSite(27, 27, STRUCTURE_EXTENSION)
//            3 -> controller.room.createConstructionSite(26, 27, STRUCTURE_EXTENSION)
//            4 -> controller.room.createConstructionSite(25, 27, STRUCTURE_EXTENSION)
//            5 -> controller.room.createConstructionSite(24, 27, STRUCTURE_EXTENSION)
//            6 -> controller.room.createConstructionSite(23, 27, STRUCTURE_EXTENSION)
//        }
//    }



    for ((_, creep) in Game.creeps) {
        if (creep.spawning) {
            continue
        }
        when (creep.memory.role) {
            Role.ASSAULTER -> { CurrentGameState.assaultTargetRoomName?.isNotBlank().let { creep.assault(CurrentGameState.assaultTargetRoomName!!) }  }
            Role.REPAIRER -> creep.repair()
            Role.TRUCKER -> creep.truck()
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

@Suppress("UNUSED_PARAMETER")
private fun test(spawn: StructureSpawn) {

}

private fun towerAction(tower: StructureTower) {
    val hostileRange = 15

    val currentRoomState = CurrentGameState.roomStates[tower.room.name]
            ?: throw RuntimeException("Missing current room status for ${tower.room.name}")

    tower.room.visual
            .circle(tower.pos, options {
                radius = hostileRange.toDouble()
                fill = "transparent"
                stroke = "red"
                lineStyle = LINE_STYLE_DOTTED
                opacity = 0.15
            })


    val hostileCreeps = tower.pos.findInRange(FIND_HOSTILE_CREEPS, hostileRange)
    if (hostileCreeps.isNotEmpty()) {
        Game.notify("Spotted enemy creep ${hostileCreeps[0].owner.username}")
        tower.attack(hostileCreeps[0])

        return
    }

    if (tower.store.getUsedCapacity(RESOURCE_ENERGY) > TOWER_CAPACITY - 250) {
        val damagedStructures = currentRoomState.damagedStructures.filter { it.isStructureTypeOf(arrayOf<StructureConstant>(STRUCTURE_ROAD, STRUCTURE_CONTAINER, STRUCTURE_RAMPART)) && it.isHpBelowPercent(100) }
        if (damagedStructures.isNotEmpty()) {
            tower.repair(damagedStructures[0])

            return
        }
    }
}

private fun spawnTrucker(
        creeps: Array<Creep>,
        spawn: StructureSpawn
):Boolean {
    val role: Role = when {
        creeps.count { it.memory.role == Role.TRUCKER } < 2 -> Role.TRUCKER
        else -> return false
    }
    val body = arrayOf<BodyPartConstant>(
            CARRY, CARRY, CARRY, CARRY, CARRY, CARRY, CARRY, CARRY, CARRY, CARRY, CARRY, CARRY, CARRY,
            MOVE, MOVE, MOVE, MOVE, MOVE, MOVE, MOVE, MOVE, MOVE, MOVE, MOVE, MOVE, MOVE
    )

    val bodyPartsCost = body.sumBy { BODYPART_COST[it]!! }
    if (spawn.room.energyAvailable < bodyPartsCost) {
        return false
    }

    val newName = "${role.name}_${bodyPartsCost}_${Game.time}"
    val code = spawn.spawnCreep(body, newName, options {
        memory = jsObject<CreepMemory> { this.role = role }
        energyStructures = getSpawnEnergyStructures(spawn).unsafeCast<Array<StoreOwner>>()
    })

    return when (code) {
        OK -> {
            console.log("Spawning \"$newName\" with body \'$body\' cost: $bodyPartsCost"); true
        }
        ERR_BUSY, ERR_NOT_ENOUGH_ENERGY -> false
        else -> {
            console.log("unhandled error code $code"); false
        }
    }
}
private fun spawnAssaulter(
        creeps: Array<Creep>,
        spawn: StructureSpawn
):Boolean {
    val role: Role = when {
        creeps.count { it.memory.role == Role.ASSAULTER } < 2 -> Role.ASSAULTER
        else -> return false
    }
    val body = arrayOf<BodyPartConstant>(
            TOUGH, TOUGH, TOUGH, TOUGH,
            ATTACK, ATTACK, ATTACK, ATTACK, ATTACK,
            CLAIM,
            MOVE, MOVE, MOVE, MOVE, MOVE, MOVE, MOVE, MOVE, MOVE, MOVE
    )

    val bodyPartsCost = body.sumBy { BODYPART_COST[it]!! }
    if (spawn.room.energyAvailable < bodyPartsCost) {
        return false
    }

    val newName = "${role.name}_${bodyPartsCost}_${Game.time}"
    val code = spawn.spawnCreep(body, newName, options {
        memory = jsObject<CreepMemory> { this.role = role }
        energyStructures = getSpawnEnergyStructures(spawn).unsafeCast<Array<StoreOwner>>()
    })

    return when (code) {
        OK -> {
            console.log("Spawning \"$newName\" with body \'$body\' cost: $bodyPartsCost"); true
        }
        ERR_BUSY, ERR_NOT_ENOUGH_ENERGY -> false
        else -> {
            console.log("unhandled error code $code"); false
        }
    }
}

//Refactor this method
private fun spawnBigHarvesters(
        creeps: Array<Creep>,
        spawn: StructureSpawn
): Boolean {
    val role: Role = when {
        creeps.count { it.memory.role == Role.HARVESTER } < 3 -> Role.HARVESTER
        else -> return false
    }

    val body = arrayOf<BodyPartConstant>(
            MOVE, MOVE, MOVE, WORK, WORK, WORK, WORK, WORK, CARRY
    )

    val bodyPartsCost = body.sumBy { BODYPART_COST[it]!! }
    if (spawn.room.energyAvailable < bodyPartsCost) {
        return false
    }

    val newName = "${role.name}_${bodyPartsCost}_${Game.time}"
    val code = spawn.spawnCreep(body, newName, options {
        memory = jsObject<CreepMemory> {
            this.role = role
            this.harvestAndDeliver = creeps.count{ it.memory.role == Role.TRUCKER } == 0
        }
        energyStructures = getSpawnEnergyStructures(spawn).unsafeCast<Array<StoreOwner>>()
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

    val currentRoomState = CurrentGameState.roomStates[spawn.room.name]
            ?: throw RuntimeException("Missing current room status for ${spawn.room.name}")

    val damagedStructures = mutableListOf<Structure>()
    damagedStructures.addAll(currentRoomState.damagedStructures.filter { it.isHpBelowPercent(80) && !it.isStructureTypeOf(arrayOf<StructureConstant>(STRUCTURE_CONTROLLER, STRUCTURE_WALL)) })
    damagedStructures.addAll(currentRoomState.damagedStructures.filter { it.isHpBelowPercent(1) && it.isStructureTypeOf(STRUCTURE_WALL) })

    var minimumUpgraders = when (spawn.room.controller?.level) {
        0, 1, 2 -> 2
        3, 4 -> 3
        5, 6, 7, 8 -> 4
        else -> 0
    }

    if (
            spawn.room.energyAvailable == spawn.room.energyCapacityAvailable &&
            currentRoomState.energyContainers.isNotEmpty() && currentRoomState.energyContainersTotalLevel == currentRoomState.energyContainersTotalMaximumLevel
    ) {
        minimumUpgraders += 2
    }

    val role: Role = when {

        creeps.count { it.memory.role == Role.HARVESTER } < 3 -> Role.HARVESTER
        creeps.count { it.memory.role == Role.TRUCKER } < 1 -> Role.TRUCKER

        spawn.room.find(FIND_MY_CONSTRUCTION_SITES).isNotEmpty() &&
                creeps.count { it.memory.role == Role.BUILDER } < 3 -> Role.BUILDER
        creeps.count { it.memory.role == Role.UPGRADER } < minimumUpgraders -> Role.UPGRADER

        damagedStructures.isNotEmpty() && creeps.count { it.memory.role == Role.REPAIRER } < 2 -> Role.REPAIRER

        else -> return false
    }

    val newName = "${role.name}_${bodyPartsCost}_${Game.time}"
    val code = spawn.spawnCreep(body, newName, options {
        memory = jsObject<CreepMemory> { this.role = role }
        energyStructures = getSpawnEnergyStructures(spawn).unsafeCast<Array<StoreOwner>>()
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

private fun getSpawnEnergyStructures(spawn: StructureSpawn): Array<Structure> {

    val currentRoomState = CurrentGameState.roomStates[spawn.room.name]
            ?: throw RuntimeException("Missing current room status for ${spawn.room.name}")

    val spawnEnergyStructures = currentRoomState.spawnEnergyStructures[spawn.id]
            ?: throw RuntimeException("Missing spawn energy structures configuration")

    return spawnEnergyStructures.toTypedArray()
}
