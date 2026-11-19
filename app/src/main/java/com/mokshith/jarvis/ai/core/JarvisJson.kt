package com.mokshith.jarvis.ai.core

import kotlinx.serialization.json.Json

object JarvisJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }
}
