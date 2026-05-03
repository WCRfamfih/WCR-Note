package com.example.ainote.data.remote

import java.io.IOException

class AiApiException(
    val userMessage: String,
    cause: Throwable? = null
) : IOException(userMessage, cause)
