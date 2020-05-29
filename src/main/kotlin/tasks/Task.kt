package tasks

import screeps.api.Game

abstract class Task(
        val name: String,
        val target: TaskTarget,
        val options: TaskOptions
) {
    var parent: ProtoTask? = null
    var tick: Int = Game.time

    fun toProtoTask():ProtoTask {
        return ProtoTask(
                this.name,
                this.target,
                this.parent,
                this.tick,
                this.options
        )
    }
}