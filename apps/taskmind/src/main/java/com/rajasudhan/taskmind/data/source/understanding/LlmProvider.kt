package com.rajasudhan.taskmind.data.source.understanding

interface LlmProvider {
    suspend fun generate(systemMessage: String, userMessage: String): String
}
