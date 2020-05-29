package tasks

import global.ProtoPos

data class TaskTarget(
        val ref: String,
        val pos: ProtoPos
)