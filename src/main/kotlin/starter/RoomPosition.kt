package starter

import screeps.api.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

fun RoomPosition.getAdjacent(): List<RoomPosition> {
    val room = Game.rooms[roomName]
    val results = mutableListOf<RoomPosition>()

    // Row -1
    if (y - 1 >= 0) {
        if (x - 1 >= 0) {
            val adjacentPos = room?.getPositionAt(x - 1, y - 1)
            if (adjacentPos != null) {
                results.add(adjacentPos)
            }
        }

        val adjacentPos = room?.getPositionAt(x, y - 1)
        if (adjacentPos != null) {
            results.add(adjacentPos)
        }

        if (x + 1 <= 49) {
            val adjacentPos = room?.getPositionAt(x + 1, y - 1)
            if (adjacentPos != null) {
                results.add(adjacentPos)
            }
        }
    }

    // Row 0
    if (this.x - 1 >= 0) {
        val adjacentPos = room?.getPositionAt(x - 1, y)
        if (adjacentPos != null) {
            results.add(adjacentPos)
        }
    }

    if (this.x + 1 <= 49) {
        val adjacentPos = room?.getPositionAt(x + 1, y)
        if (adjacentPos != null) {
            results.add(adjacentPos)
        }
    }


    // Row +1
    if (this.y + 1 <= 49) {
        if (this.x - 1 >= 0) {
            val adjacentPos = room?.getPositionAt(x - 1, y + 1)
            if (adjacentPos != null) {
                results.add(adjacentPos)
            }
        }
        val adjacentPos = room?.getPositionAt(x, y + 1)
        if (adjacentPos != null) {
            results.add(adjacentPos)
        }
        if (this.x + 1 <= 49) {
            val adjacentPos = room?.getPositionAt(x + 1, y + 1)
            if (adjacentPos != null) {
                results.add(adjacentPos)
            }
        }
    }

    return results.toList()
}

fun RoomPosition.getSteppableAdjacent(includeCreeps: Boolean = false, includeStructures: Boolean = true): List<RoomPosition> {
    return getAdjacent().filter { it.isSteppable(includeCreeps, includeStructures) }
}

fun RoomPosition.getAdjacentInRange(range: Int = 1): List<RoomPosition> {
    val bounds = createBoundingBoxForRange(x, y, range)
    val positions = mutableListOf<RoomPosition>()

    for (x in bounds.left until bounds.right) {
        for (y in bounds.top downTo bounds.bottom) {
            positions.add(RoomPosition(x, y, roomName))
        }
    }

    return positions.toList()
}

fun RoomPosition.getSteppableAdjacentInRange(range: Int): List<RoomPosition> {
    val bounds = createBoundingBoxForRange(x, y, range)
    val positions = mutableListOf<RoomPosition>()

    for (x in bounds.left until bounds.right) {
        for (y in bounds.top downTo bounds.bottom) {
            val position = RoomPosition(x, y, roomName)
            if (position.isSteppable()) positions.add(position)
        }
    }

    return positions
}

fun RoomPosition.isEdge(): Boolean {
    return x == 49 || x == 0 || y == 49 || y == 0
}

fun RoomPosition.isExit(): Boolean {
    return isEdge() && getTerrainAt() != TERRAIN_WALL
}

fun RoomPosition.inFrontOfExit(): Boolean {
    if (isEdge()) {
        return false
    }

    getAdjacent().forEach {
        if (it.isExit()) {
            return true
        }
    }

    return false
}

fun RoomPosition.getMostOpenNeighbor(isBuildable: Boolean = false, includeStructures: Boolean = true): RoomPosition? {
    var bestPosition: RoomPosition? = null
    var score = 0

    for (position in getSteppableAdjacent()) {
        if (isBuildable && (position.inFrontOfExit() || position.isEdge())) {
            continue
        }

        val positionScore = position.getSteppableAdjacent(false, includeStructures).count()
        if (positionScore > score) {
            score = positionScore
            bestPosition = position
        }
    }

    return bestPosition
}

data class BoundingBox(val left: Int, val right: Int, val top: Int, val bottom: Int)

fun RoomPosition.createBoundingBoxForRange(x: Int, y: Int, range: Int): BoundingBox {
    val absRange = abs(range)
    val left = min(max(x - absRange, 0), 49)
    val right = max(min(x + absRange, 49), 0)
    val top = min(max(y - absRange, 0), 49)
    val bottom = max(min(y + absRange, 49), 0)

    return BoundingBox(left, right, top, bottom)
}

fun RoomPosition.getTerrainAt(): TerrainConstant {
    return when (Game.map.getRoomTerrain(roomName).get(x, y)) {
        TERRAIN_MASK_NONE -> TERRAIN_PLAIN
        TERRAIN_MASK_WALL -> TERRAIN_WALL
        TERRAIN_MASK_SWAMP -> TERRAIN_SWAMP
        else -> error("No mask found")
    }
}

fun RoomPosition.isSteppable(includeCreeps: Boolean = true, includeStructures: Boolean = true): Boolean {
    if (getTerrainAt() == TERRAIN_WALL && lookFor(LOOK_STRUCTURES)?.none { it.structureType == STRUCTURE_ROAD } == true) {
        return false
    }

    if (includeStructures) {
        val structures = lookFor(LOOK_STRUCTURES)

        structures?.forEach {
            if (OBSTACLE_OBJECT_TYPES.indexOf(it.structureType.toString()) >= 0) {
                return false
            }
        }
    }

    if (includeCreeps) {
        if (!lookFor(LOOK_CREEPS).isNullOrEmpty()) {
            return false
        }
    }

    return true
}

fun RoomPosition.getBoundingBoxForRange(range: Int): BoundingBox {
    return createBoundingBoxForRange(x, y, range)
}