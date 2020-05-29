package tasks

import global.ProtoPos

data class TaskOptions(val blind: Boolean = false, val nextPos: ProtoPos? = null)
