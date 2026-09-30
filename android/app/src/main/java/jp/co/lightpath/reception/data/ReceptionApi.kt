package jp.co.lightpath.reception.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ReceptionResponse(
    val id: String,
    val staffName: String,
    val partySize: Int,
    val acceptedAt: String,
    val notified: Boolean,
)

open class ReceptionApi(
    baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build(),
) {
    private val root = baseUrl.trimEnd('/')

    open fun createReception(staffName: String, partySize: Int): ReceptionResponse {
        val json = JSONObject()
            .put("staffName", staffName)
            .put("partySize", partySize)
            .toString()

        val request = Request.Builder()
            .url("$root/receptions")
            .post(json.toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: $body")
            }
            val obj = JSONObject(body)
            return ReceptionResponse(
                id = obj.getString("id"),
                staffName = obj.getString("staffName"),
                partySize = obj.getInt("partySize"),
                acceptedAt = obj.getString("acceptedAt"),
                notified = obj.getBoolean("notified"),
            )
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
