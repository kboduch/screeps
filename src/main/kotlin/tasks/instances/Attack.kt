package tasks.instances

import tasks.*

class Attack(
        target: TaskTarget,
        options: TaskOptions
) : Task(
        "attack",
        target,
        options
)
