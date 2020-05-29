package global

import screeps.api.Creep

data class ProtoPos(
        val x: Int = -1,
        val y: Int = -1,
        val roomName: String = ""
) {
    constructor(creep: Creep) : this(creep.pos.x, creep.pos.y, creep.pos.roomName)
}
