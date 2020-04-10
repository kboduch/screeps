package starter

import screeps.api.STRUCTURE_CONTAINER
import screeps.api.STRUCTURE_STORAGE
import screeps.api.structures.Structure

fun Structure.isEnergyContainer(): Boolean {
    return this.structureType == STRUCTURE_CONTAINER || this.structureType == STRUCTURE_STORAGE
}
