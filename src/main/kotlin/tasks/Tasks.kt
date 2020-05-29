package tasks

import tasks.instances.Attack

class Tasks {
    companion object {
        fun attack(target: TaskTarget, options: TaskOptions = TaskOptions()): Attack {
            return Attack(target, options)
        }
    }
}