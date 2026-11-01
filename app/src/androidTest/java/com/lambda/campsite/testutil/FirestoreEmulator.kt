/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, the emulator's
 * REST calls, syntax and KDoc. It also helped add decimal numbers for the
 * campsite tests. It also helped add timestampValue encoding for the
 * notification tests. Sashreek Addanki reviewed the string, boolean, number
 * and list encoding, and Aarush Bhat reviewed the timestamp encoding.
 */
package com.lambda.campsite.testutil

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.app
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/**
 * Talks to the Firestore emulator directly, for setting up tests.
 *
 * These calls skip the security rules, the way an admin's or a Cloud
 * Function's writes do. Use them to set up data a user isn't allowed to write
 * themselves, then test what the user can do with it.
 */
object FirestoreEmulator {

    private val projectId: String
        get() = requireNotNull(Firebase.app.options.projectId)

    private val baseUrl = "http://${LambdaTestRunner.HOST}:${LambdaTestRunner.FIRESTORE_PORT}"

    /** Deletes every document, so each test starts with an empty database. */
    fun clear() {
        send("DELETE", "$baseUrl/emulator/v1/projects/$projectId/databases/(default)/documents")
    }

    /**
     * Replaces the document at [path], like `profiles/abc`, with [fields],
     * skipping the security rules. Values can be strings, booleans, numbers,
     * timestamps, lists of those, or `null`.
     */
    fun setIgnoringRules(path: String, fields: Map<String, Any?>) {
        val name = "projects/$projectId/databases/(default)/documents/$path"
        val write = JSONObject().put("update", JSONObject().put("name", name).put("fields", encodeFields(fields)))
        val body = JSONObject().put("writes", JSONArray().put(write))
        send("POST", "$baseUrl/v1/projects/$projectId/databases/(default)/documents:commit", body.toString())
    }

    private fun encodeFields(fields: Map<String, Any?>): JSONObject =
        JSONObject().apply { fields.forEach { (key, value) -> put(key, encode(value)) } }

    // Firestore's REST API wraps every value in an object that names its type.
    private fun encode(value: Any?): JSONObject = when (value) {
        null -> JSONObject().put("nullValue", JSONObject.NULL)
        is String -> JSONObject().put("stringValue", value)
        is Boolean -> JSONObject().put("booleanValue", value)
        is Int, is Long -> JSONObject().put("integerValue", value.toString())
        is Double -> JSONObject().put("doubleValue", value)
        is Timestamp -> JSONObject().put(
            "timestampValue",
            Instant.ofEpochSecond(value.seconds, value.nanoseconds.toLong()).toString(),
        )
        is List<*> -> JSONObject().put(
            "arrayValue",
            JSONObject().put("values", JSONArray().apply { value.forEach { put(encode(it)) } }),
        )
        else -> throw IllegalArgumentException("Can't send a ${value::class.simpleName} to the emulator.")
    }

    private fun send(method: String, url: String, body: String? = null) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            // "owner" is the emulator's admin token. It skips the security rules.
            connection.setRequestProperty("Authorization", "Bearer owner")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            check(code in 200..299) { "The Firestore emulator answered $code to $method $url." }
        } finally {
            connection.disconnect()
        }
    }
}
