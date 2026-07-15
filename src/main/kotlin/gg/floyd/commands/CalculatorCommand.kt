package gg.floyd.commands

import com.github.stivais.commodore.Commodore
import gg.floyd.features.impl.misc.FloydCalculator
import gg.floyd.utils.handlers.schedule

val calculatorCommand = Commodore("calculator", "calc") {
    runs {
        schedule(0) { FloydCalculator.toggleHudVisibility() }
    }
}
