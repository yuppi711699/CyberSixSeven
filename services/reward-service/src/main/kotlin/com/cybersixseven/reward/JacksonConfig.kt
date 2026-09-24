package com.cybersixseven.reward

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.module.kotlin.KotlinModule

@Configuration
class JacksonConfig {
    @Bean
    fun kotlinModuleCustomizer(): JsonMapperBuilderCustomizer =
        JsonMapperBuilderCustomizer { builder ->
            builder.addModule(KotlinModule.Builder().build())
        }
}
