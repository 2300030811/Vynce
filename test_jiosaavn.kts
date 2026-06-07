import kotlinx.coroutines.runBlocking
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

fun main() = runBlocking {
    val client = HttpClient(OkHttp)
    try {
        val res = client.get("https://jiosaavn-api-pink.vercel.app/api/songs/3/suggestions?limit=5")
        println(res.bodyAsText())
    } catch (e: Exception) {
        println(e.message)
    } finally {
        client.close()
    }
}
