package com.spatialkit

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SpatialKitApplication

fun main(args: Array<String>) {
    runApplication<SpatialKitApplication>(*args)
}
