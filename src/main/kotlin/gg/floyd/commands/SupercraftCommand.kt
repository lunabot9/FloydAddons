package gg.floyd.commands

import com.github.stivais.commodore.Commodore
import com.github.stivais.commodore.utils.GreedyString
import gg.floyd.supercraft.SackSupercraft

val supercraftCommand=Commodore("supercraft") {
    runs { SackSupercraft.listSacks() }
    literal("stop").runs { SackSupercraft.stop("Stopped by command") }
    literal("exceptions").runs { SackSupercraft.listExceptions() }
    literal("except").executable {
        param("item") { suggests { SackSupercraft.itemNames() } }
        runs { item:GreedyString -> SackSupercraft.exception(item.string,true) }
    }
    literal("allow").executable {
        param("item") { suggests { SackSupercraft.itemNames() } }
        runs { item:GreedyString -> SackSupercraft.exception(item.string,false) }
    }
    executable {
        param("sack") { suggests { SackSupercraft.names() } }
        runs { sack:GreedyString -> SackSupercraft.start(sack.string) }
    }
}
