package com.odtheking.odin.utils

import net.minecraft.world.phys.Vec3

operator fun Vec3.unaryMinus(): Vec3 = Vec3(-x, -y, -z)

fun Vec3.addVec(x: Number = 0.0, y: Number = 0.0, z: Number = 0.0): Vec3 =
    Vec3(this.x + x.toDouble(), this.y + y.toDouble(), this.z + z.toDouble())
