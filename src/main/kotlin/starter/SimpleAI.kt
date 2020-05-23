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
            CurrentGameState.roomStates[room.name] = CurrentRoomState(room) //todo change to myRoomStates
        }
        //todo add otherRoomStates (neutral and hostile)
    }

    CurrentGameState.assaultTargetRoomName = null
    Game.flags.values.forEach { flag ->
        if (flag.name == "Assault" && flag.memory.roomName != null) {
            CurrentGameState.assaultTargetRoomName = flag.memory.roomName
        }
    }

    CurrentGameState.roomStates.forEach {(roomName, currentRoomState) ->
        val roomCreeps = Game.creeps.values.filter { it.room.name == roomName }.toTypedArray()

        if (Game.time % 20 == 0) {
            val areThereAnyTruckersPresentInRoom = roomCreeps.count { it.memory.role == Role.TRUCKER } > 0
            roomCreeps.forEach {
                if (it.memory.role == Role.HARVESTER) {
                    it.memory.harvestAndDeliver = areThereAnyTruckersPresentInRoom.not()
                }
            }
        }

        currentRoomState.room.visual.text(
                "$roomName ${currentRoomState.room.energyAvailable}/${currentRoomState.room.energyCapacityAvailable} ${currentRoomState.energyContainersTotalLevel}/${currentRoomState.energyContainersTotalMaximumLevel}",
                0.0,
                0.0,
                options { align = screeps.api.TEXT_ALIGN_LEFT }
        )

        val mainSpawn = currentRoomState.myStructures.firstOrNull { it.isStructureTypeOf(STRUCTURE_SPAWN) && it.unsafeCast<StructureSpawn>().spawning == null }

        if (null != mainSpawn) {
            when (true) {
                spawnBigHarvesters(roomCreeps, mainSpawn as StructureSpawn) -> {}
                CurrentGameState.assaultTargetRoomName != null && spawnAssaulter(Game.creeps.values, mainSpawn) -> {}
                spawnTrucker(roomCreeps, mainSpawn) -> {}
                spawnCreeps(arrayOf(MOVE, MOVE, MOVE, WORK, WORK, WORK, CARRY, CARRY, CARRY), roomCreeps, mainSpawn) -> {} //600
                spawnCreeps(arrayOf(MOVE, MOVE, WORK, WORK, WORK, CARRY), roomCreeps, mainSpawn) -> {}
                spawnCreeps(arrayOf(WORK, CARRY, MOVE, MOVE), roomCreeps, mainSpawn) -> {}
                spawnEnergyVentCreeps(roomCreeps, mainSpawn as StructureSpawn) -> {}
            }
        } else {
            val flagList = Game.flags.values.filter { it.name == "spawn" }

            if (flagList.isNotEmpty()) {
                val spawnPlacement = flagList.first()
                when (currentRoomState.room.createConstructionSite(spawnPlacement.pos.x, spawnPlacement.pos.y, STRUCTURE_SPAWN)){

                }
            }
        }
    }

    //todo remove
    val mainSpawn = Game.spawns.values.firstOrNull { it.isStructureTypeOf(STRUCTURE_SPAWN) }
    val constructionSitez = arrayListOf<ConstructionSite>()
    CurrentGameState.roomStates.forEach {
        if (it.value.spawnEnergyStructures.isEmpty()) {
            constructionSitez.addAll(it.value.constructionSites)
        }
    }

    if (constructionSitez.isNotEmpty() && mainSpawn != null && null == mainSpawn.spawning) {
        spawnRBuilders(Game.creeps.values, mainSpawn)
    }

    //delete memories of creeps that have passed away
    houseKeeping(Game.creeps)

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


    for ((_, structure) in Game.structures) {
        when (structure.structureType) {
            STRUCTURE_TOWER -> towerAction(structure as StructureTower)
        }
    }

    for ((_, creep) in Game.creeps) {
        if (creep.spawning) {
            continue
        }
        when (creep.memory.role) {
            Role.ASSAULTER -> {
                if (CurrentGameState.assaultTargetRoomName != null) {
                    creep.assault(CurrentGameState.assaultTargetRoomName!!)
                } else {
                    creep.assault()
                }
            }
            Role.REPAIRER -> creep.repair()
            Role.TRUCKER -> creep.truck()
            Role.HARVESTER -> creep.harvest()
            Role.BUILDER -> creep.build()
            Role.UPGRADER -> creep.upgrade()
            Role.RBUILDER -> creep.build(constructionSitez.first().room ?: creep.room)
            else -> creep.pause()
        }
    }
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
        val damagedStructures = currentRoomState.damagedStructures.filter { it.isStructureTypeOf(arrayOf<StructureConstant>(STRUCTURE_ROAD, STRUCTURE_CONTAINER)) && it.isHpBelowPercent(100) }
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
            MOVE, MOVE, MOVE, MOVE, MOVE, CARRY, CARRY, CARRY, CARRY, CARRY, CARRY, CARRY, CARRY, CARRY, CARRY
    )

    val bodyPartsCost = body.sumBy { BODYPART_COST[it]!! }
    if (spawn.room.energyAvailable < bodyPartsCost) {
        return false
    }

    val newName = creepNameGenerator(role, bodyPartsCost)
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
        creeps.filter { it.memory.role == Role.ASSAULTER }.sumBy { it.ticksToLive } < 100 -> Role.ASSAULTER
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

    val newName = creepNameGenerator(role, bodyPartsCost)
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

    val newName = creepNameGenerator(role, bodyPartsCost)
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

private fun spawnRBuilders(
        creeps: Array<Creep>,
        spawn: StructureSpawn
): Boolean {
    val role: Role = when {
        creeps.count { it.memory.role == Role.RBUILDER } < 6 -> Role.RBUILDER
        else -> return false
    }

    val body = arrayOf<BodyPartConstant>(
            MOVE, MOVE, MOVE, MOVE, MOVE, MOVE, WORK, WORK, WORK, WORK, WORK, CARRY
    )

    val bodyPartsCost = body.sumBy { BODYPART_COST[it]!! }
    if (spawn.room.energyAvailable < bodyPartsCost) {
        return false
    }

    val newName = creepNameGenerator(role, bodyPartsCost)
    val code = spawn.spawnCreep(body, newName, options {
        memory = jsObject<CreepMemory> {
            this.role = role
            this.fallbackRoom = spawn.room.name
        }
        energyStructures = getSpawnEnergyStructures(spawn).unsafeCast<Array<StoreOwner>>()
    })

    return when (code) {
        OK -> {console.log("Spawning \"$newName\" with body \'$body\' cost: $bodyPartsCost") ; true }
        ERR_BUSY, ERR_NOT_ENOUGH_ENERGY -> false
        else -> { console.log("unhandled error code $code"); false }
    }
}

private fun spawnEnergyVentCreeps(
        creeps: Array<Creep>,
        spawn: StructureSpawn
): Boolean {

    val role: Role = when {
        creeps.count { it.memory.role == Role.UPGRADER } < 10 -> Role.UPGRADER
        else -> return false
    }

    val currentRoomState = CurrentGameState.roomStates[spawn.room.name]
            ?: throw RuntimeException("Missing current room status for ${spawn.room.name}")

    val storageStructures = currentRoomState.energyContainers.filter { it.isStructureTypeOf(STRUCTURE_STORAGE) }
    if (spawn.room.energyAvailable == spawn.room.energyCapacityAvailable && storageStructures.isNotEmpty()) {
        val currentEnergy = storageStructures.sumBy {
            it.unsafeCast<StoreOwner>().store.getUsedCapacity(RESOURCE_ENERGY) ?: 0
        }

        val totalResourceCapacity = storageStructures.sumBy {
            it.unsafeCast<StoreOwner>().store.getCapacity() ?: 0
        }

        if (totalResourceCapacity == 0) {
            return false
        }

        val storageUsedByEnergyPercentage = currentEnergy * 100 / totalResourceCapacity

        if (storageUsedByEnergyPercentage > 74) {
            val body = arrayOf<BodyPartConstant>(
                    MOVE, MOVE, MOVE, MOVE, MOVE, MOVE, MOVE, MOVE, WORK, WORK, WORK, WORK, WORK, WORK, WORK, WORK, WORK, WORK, CARRY, CARRY, CARRY, CARRY, CARRY, CARRY
            )

            val bodyPartsCost = body.sumBy { BODYPART_COST[it]!! }
            if (spawn.room.energyAvailable < bodyPartsCost) {
                return false
            }

            val newName = creepNameGenerator(role, bodyPartsCost)
            val code = spawn.spawnCreep(body, newName, options {
                memory = jsObject<CreepMemory> {
                    this.role = role
                }
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
    }

    return false
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
    damagedStructures.addAll(currentRoomState.damagedStructures.filter { it.isHpBelow(2500000) && it.isStructureTypeOf(STRUCTURE_WALL) }) //tmp

    var minimumUpgraders = when (spawn.room.controller?.level) {
        0, 1, 2 -> 2
        3, 4 -> 3
        5, 6, 7, 8 -> 4
        else -> 0
    }

    val role: Role = when {

        spawn.room.find(FIND_MY_CONSTRUCTION_SITES).isNotEmpty() && creeps.count { it.memory.role == Role.BUILDER } < 2 -> Role.BUILDER
        creeps.count { it.memory.role == Role.HARVESTER } < 3 -> Role.HARVESTER
        creeps.count { it.memory.role == Role.UPGRADER } < minimumUpgraders -> Role.UPGRADER

        damagedStructures.isNotEmpty() && creeps.count { it.memory.role == Role.REPAIRER } < 1 -> Role.REPAIRER

        else -> return false
    }

    val newName = creepNameGenerator(role, bodyPartsCost)
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
