package com.opoojkk.podium

class JVMPlatform : Platform {
    override val name: String = "JVM Desktop - Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()