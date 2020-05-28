package global

import screeps.api.Game
import screeps.api.get
import starter.parkX
import starter.parkY

@Suppress("unused")
@JsName("setParkingCords")
fun setParkingCords(roomName: String, x: Int, y: Int) {
    val room = Game.rooms[roomName]
    if (room != null) {
        room.memory.parkX = x
        room.memory.parkY = y
    }
}
