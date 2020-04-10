package starter

import screeps.api.STRUCTURE_CONTAINER
import screeps.api.STRUCTURE_STORAGE
import screeps.api.structures.Structure

fun Structure.isEnergyContainer(): Boolean {
    return this.structureType == STRUCTURE_CONTAINER || this.structureType == STRUCTURE_STORAGE
}

fun Structure.isHpBelowPercent(percent: Int): Boolean {
    return ((hits * 100) / hitsMax) < percent
}

fun Structure.isHpBelow(amount: Int): Boolean {
    return hits < amount
}
