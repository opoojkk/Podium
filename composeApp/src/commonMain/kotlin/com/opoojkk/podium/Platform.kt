package com.opoojkk.podium

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform