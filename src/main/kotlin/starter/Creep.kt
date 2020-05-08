package starter

import screeps.api.Creep

fun Creep.getTargetId(): String? {
    return this.memory.targetId
}

fun Creep.setTargetId(targetId: String?): Unit {
    this.memory.targetId = targetId
}

fun Creep.clearTargetId(): Unit {
    this.setTargetId(null)
}
