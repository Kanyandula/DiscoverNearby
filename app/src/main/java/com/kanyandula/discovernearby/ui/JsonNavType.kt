package com.kanyandula.discovernearby.ui

import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Carries a @Serializable value in a type-safe route as JSON (routes otherwise take only primitives). The value
 * also lives in the back stack's saved state, so the destination keeps it across process death. A nullable [T] takes
 * the nullable serializer, which also makes the route value nullable.
 */
internal class JsonNavType<T>(private val serializer: KSerializer<T>) :
    NavType<T>(isNullableAllowed = serializer.descriptor.isNullable) {

    override fun put(bundle: Bundle, key: String, value: T) =
        bundle.putString(key, Json.encodeToString(serializer, value))

    override fun get(bundle: Bundle, key: String): T? = bundle.getString(key)?.let(::parseValue)

    override fun parseValue(value: String): T = Json.decodeFromString(serializer, value)

    // Navigation builds a route string from this: the JSON's quotes and braces and the name need encoding.
    override fun serializeAsValue(value: T): String = Uri.encode(Json.encodeToString(serializer, value))
}
