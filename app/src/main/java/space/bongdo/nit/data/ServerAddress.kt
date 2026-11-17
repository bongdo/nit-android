package space.bongdo.nit.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class ServerAddress(
    val origin: String,
    val basePath: String,
    val display: String,
) {
    val apiRoot: String get() = origin + basePath
    val loginUrl: String get() = origin + "/login"

    fun api(path: String): String = apiRoot + path
}

fun parseServer(input: String): ServerAddress? {
    var raw = input.trim()
    if (raw.isEmpty()) return null
    if (!raw.contains("://")) raw = "https://$raw"
    raw = raw.trimEnd('/')
    val url = raw.toHttpUrlOrNull() ?: return null
    if (url.host.isBlank()) return null
    if (url.scheme == "http" && url.host !in setOf("localhost", "127.0.0.1", "10.0.2.2")) return null
    val origin = "${url.scheme}://${url.host}${if (url.port != (if (url.scheme == "https") 443 else 80)) ":${url.port}" else ""}"
    val path = url.encodedPath.trimEnd('/')
    return ServerAddress(
        origin = origin,
        basePath = path,
        display = origin + path,
    )
}

fun normalizeServer(input: String): String {
    val parsed = parseServer(input) ?: return input.trim()
    return parsed.display
}
