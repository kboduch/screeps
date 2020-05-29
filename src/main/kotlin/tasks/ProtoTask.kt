package tasks

data class ProtoTask(
        val name: String,
        val target: TaskTarget,
        val parent: ProtoTask?,
        val tick: Int,
        val options: TaskOptions
)