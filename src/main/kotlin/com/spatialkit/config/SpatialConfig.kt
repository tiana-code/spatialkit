package com.spatialkit.config

import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@Configuration
@EnableJpaAuditing
class SpatialConfig {

    @Bean
    fun geometryFactory(): GeometryFactory = GeometryFactory(PrecisionModel(), 4326)
}
