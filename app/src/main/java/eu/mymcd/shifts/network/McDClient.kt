package eu.mymcd.shifts.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import eu.mymcd.shifts.store.SecureStore
import java.io.IOException
import java.util.concurrent.TimeUnit

data class UserInfo(
    val id: Long,
    val fullName: String
)

data class Shift(
    val id: Long,
    val date: String,
    val from: String,
    val to: String,
    val note: String?
) {
    fun sameAs(other: Shift): Boolean =
        id == other.id && date == other.date && from == other.from &&
            to == other.to && (note ?: "") == (other.note ?: "")
}

class AuthException(message: String) : Exception(message)
class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** HTTP client bound to one stored account (own cookie jar). */
class McDClient(
    private val store: SecureStore,
    private val accountId: String
) {

    private val cookieStore = object : CookieJar {
        private val cache = LinkedHashMap<String, Cookie>()
        private var restored = false

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            for (cookie in cookies) {
                if (cookie.expiresAt < System.currentTimeMillis()) {
                    cache.remove(keyFor(cookie))
                } else {
                    cache[keyFor(cookie)] = cookie
                }
            }
            persist()
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            restoreIfNeeded()
            val now = System.currentTimeMillis()
            return cache.values.filter { it.expiresAt > now && it.matches(url) }
        }

        fun clear() {
            cache.clear()
            restored = true
            store.saveCookie(accountId, "")
        }

        private fun keyFor(c: Cookie): String = "${c.domain}|${c.path}|${c.name}"

        private fun restoreIfNeeded() {
            if (restored) return
            restored = true
            val raw = store.getCookie(accountId)
            if (raw.isEmpty()) return
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val builder = Cookie.Builder()
                        .domain(o.getString("domain"))
                        .path(o.getString("path"))
                        .name(o.getString("name"))
                        .value(o.getString("value"))
                    val expires = o.optLong("expires", 0L)
                    builder.expiresAt(if (expires > 0) expires else System.currentTimeMillis() + ONE_DAY_MS)
                    if (o.optBoolean("secure", false)) builder.secure()
                    if (o.optBoolean("httpOnly", false)) builder.httpOnly()
                    val cookie = builder.build()
                    cache[keyFor(cookie)] = cookie
                }
            } catch (_: Exception) {
            }
        }

        private fun persist() {
            try {
                val arr = JSONArray()
                for (c in cache.values) {
                    arr.put(
                        JSONObject()
                            .put("name", c.name)
                            .put("value", c.value)
                            .put("domain", c.domain)
                            .put("path", c.path)
                            .put("expires", if (c.persistent) c.expiresAt else 0L)
                            .put("secure", c.secure)
                            .put("httpOnly", c.httpOnly)
                    )
                }
                store.saveCookie(accountId, arr.toString())
            } catch (_: Exception) {
            }
        }
    }

    private val userAgentInterceptor = Interceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,application/json;q=0.8,*/*;q=0.7"
                )
                .build()
        )
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieStore)
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(userAgentInterceptor)
        .build()

    fun login(email: String, password: String): UserInfo {
        cookieStore.clear()

        execute(Request.Builder().url("$BASE/login").get().build(), allowFailure = false).close()

        val body = FormBody.Builder()
            .add("_username", email)
            .add("_password", password)
            .add("redir", "")
            .build()

        val post = Request.Builder()
            .url(LOGIN_CHECK_URL)
            .post(body)
            .header("Referer", "https://mymcd.eu/login/")
            .header("Origin", "https://mymcd.eu")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()

        val resp = execute(post, allowFailure = true)
        val html = resp.body?.string() ?: ""
        val finalUrl = resp.request.url.toString()
        resp.close()

        extractServerError(html)?.let { throw AuthException("server:$it") }
        if (finalUrl.contains("mymcd.eu/login")) {
            throw AuthException("server:Login failed")
        }

        try {
            execute(Request.Builder().url("$BASE/login").get().build(), allowFailure = true).close()
        } catch (_: Exception) {
        }

        val me = fetchUserMe()
            ?: throw AuthException("server:Login failed — session not established")

        store.setEmail(accountId, email)
        store.savePassword(accountId, password)
        store.setUserId(accountId, me.id)
        store.setFullName(accountId, me.fullName)
        store.setError(accountId, "")
        return me
    }

    fun fetchUserMe(): UserInfo? {
        val resp = execute(Request.Builder().url("$BASE/api/user/me").get().build(), allowFailure = true)
        val code = resp.code
        if (code == 401 || code == 403 || !resp.isSuccessful) {
            resp.close()
            return null
        }
        val body = resp.body?.string()
        resp.close()
        if (body.isNullOrBlank()) return null
        return try {
            val o = JSONObject(body)
            val id = o.getLong("id")
            val name = o.optString("name")
            val surname = o.optString("surname")
            val full = o.optString("fullname").ifBlank { "$name $surname".trim() }
            UserInfo(id, full)
        } catch (_: Exception) {
            null
        }
    }

    fun fetchNextShifts(userId: Long, count: Int): List<Shift> {
        val resp = execute(
            Request.Builder().url("$BASE/api/next-shifts/$userId?count=$count").get().build(),
            allowFailure = false
        )
        val text = resp.body?.string() ?: ""
        resp.close()
        return parseShifts(text)
    }

    fun refreshAll(shiftCount: Int = 10): List<Shift> {
        if (!store.hasCredentials(accountId)) throw AuthException("NOT_CONFIGURED")

        var me = fetchUserMe()
        if (me == null) {
            login(store.getEmail(accountId), store.getPassword(accountId))
            me = fetchUserMe() ?: throw AuthException("server:Session expired")
        } else {
            store.setUserId(accountId, me.id)
            store.setFullName(accountId, me.fullName)
        }

        val shifts = try {
            fetchNextShifts(me.id, shiftCount)
        } catch (e: AuthException) {
            // Session expired between /user/me and /next-shifts — re-login once.
            login(store.getEmail(accountId), store.getPassword(accountId))
            val me2 = fetchUserMe() ?: throw AuthException("server:Session expired")
            store.setUserId(accountId, me2.id)
            store.setFullName(accountId, me2.fullName)
            fetchNextShifts(me2.id, shiftCount)
        }

        store.setShifts(accountId, shiftsToJson(shifts))
        store.setLastUpdate(accountId, System.currentTimeMillis())
        store.setError(accountId, "")
        return shifts
    }

    fun loadCachedShifts(): List<Shift> = parseShifts(store.getShifts(accountId))

    private fun execute(request: Request, allowFailure: Boolean): okhttp3.Response {
        try {
            val resp = client.newCall(request).execute()
            if (!allowFailure && !resp.isSuccessful) {
                val code = resp.code
                resp.close()
                if (code == 401 || code == 403) throw AuthException("HTTP_$code")
                throw NetworkException("HTTP_$code")
            }
            return resp
        } catch (e: AuthException) {
            throw e
        } catch (e: NetworkException) {
            throw e
        } catch (e: IOException) {
            throw NetworkException(e.message ?: "network error", e)
        }
    }

    companion object {
        const val BASE = "https://next.mymcd.eu"
        const val LOGIN_CHECK_URL = "https://mymcd.eu/user/login-check/"
        private const val ONE_DAY_MS = 24L * 60 * 60 * 1000
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        fun parseShifts(json: String): List<Shift> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                val list = ArrayList<Shift>(arr.length())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        Shift(
                            id = o.optLong("id"),
                            date = o.optString("date"),
                            from = o.optString("from"),
                            to = o.optString("to"),
                            note = if (o.isNull("note")) null else o.optString("note").ifBlank { null }
                        )
                    )
                }
                list.sortedBy { it.date }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun shiftsToJson(shifts: List<Shift>): String {
            val arr = JSONArray()
            for (s in shifts) {
                arr.put(
                    JSONObject()
                        .put("id", s.id)
                        .put("date", s.date)
                        .put("from", s.from)
                        .put("to", s.to)
                        .put("note", s.note)
                )
            }
            return arr.toString()
        }

        private fun extractServerError(html: String): String? {
            if (html.isBlank()) return null
            val span = Regex(
                """<span class="error">(.*?)</span>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).find(html)
            val raw = span?.groupValues?.get(1)
                ?: if (html.contains("Bad credentials", ignoreCase = true)) "Bad credentials" else null
                ?: return null
            val text = raw
                .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("<[^>]+>"), "")
                .replace("*", "")
                .trim()
            return text.ifBlank { null }
        }
    }
}
