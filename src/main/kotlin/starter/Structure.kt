package starter

import screeps.api.*
import screeps.api.structures.Structure

fun Structure.isEnergyContainer(): Boolean {
    return this.isStructureTypeOf(arrayOf<StructureConstant>(STRUCTURE_CONTAINER, STRUCTURE_STORAGE))
}

fun Structure.isSpawnEnergyContainer(): Boolean {
    return this.isStructureTypeOf(arrayOf<StructureConstant>(STRUCTURE_EXTENSION, STRUCTURE_SPAWN))
}

fun Structure.isHpBelowPercent(percent: Int): Boolean {
    return ((hits * 100) / hitsMax) < percent
}

fun Structure.isHpBelow(amount: Int): Boolean {
    return hits < amount
}

fun Structure.isStructureTypeOf(structureTypes: Array<StructureConstant>): Boolean {
    return structureTypes.contains(this.structureType)
}

fun Structure.isStructureTypeOf(structureType: StructureConstant): Boolean {
    return this.structureType == structureType
}
