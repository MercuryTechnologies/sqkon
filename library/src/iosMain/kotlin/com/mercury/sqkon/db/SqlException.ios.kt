package com.mercury.sqkon.db

actual class SqlException(message: String? = null, cause: Throwable? = null) :
    Exception(message, cause)
