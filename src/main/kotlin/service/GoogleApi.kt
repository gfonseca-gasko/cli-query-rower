@file:Suppress("DEPRECATION")

package service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import model.SQL
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.security.interfaces.RSAPrivateKey
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.collections.ArrayList
import kotlin.system.exitProcess

class GoogleApi(private var home: String, private var configFile: String) {

    private var apiURI: String = "https://sheets.googleapis.com/v4/spreadsheets"

    private fun getAuthToken(): String {
        val authFile = File("$home/json", configFile).readText()
        val jsonFile = JSONObject(authFile)
        val credentials: GoogleCredential = GoogleCredential.fromStream(authFile.byteInputStream())
        val algorithm: Algorithm = Algorithm.RSA256(null, credentials.serviceAccountPrivateKey!! as RSAPrivateKey?)
        return JWT.create()
            .withKeyId(jsonFile.getString("private_key_id"))
            .withIssuer(jsonFile.getString("client_email"))
            .withSubject(jsonFile.getString("client_email"))
            .withAudience("https://sheets.googleapis.com/")
            .withIssuedAt(Date.from(Instant.now()))
            .withExpiresAt(Date.from(Instant.now().plusMillis(300 * 1000L)))
            .sign(algorithm)
    }

    fun getQueries(spreadsheetID: String, range: String): MutableList<SQL> {
        val sqlList: MutableList<SQL> = ArrayList()
        try {
            val request = HttpRequest.newBuilder().uri(URI.create("$apiURI/$spreadsheetID/values/$range"))
                .header("Authorization", "Bearer ${getAuthToken()}")
                .timeout(Duration.ofMinutes(5)).GET().build()
            val response = HttpClient.newBuilder().build().send(request, BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                throw Exception("Erro ao obter a planilha - StatusCode ${response.statusCode()}")
            }

            val sqlObjects = JSONObject(response.body()).getJSONArray("values")
            for (i in 0 until sqlObjects.length()) {
                val sqlFields = sqlObjects.getJSONArray(i)
                var mail = ""
                if(sqlFields.length() == 4) mail = sqlFields.getString(3)
                sqlList.add(
                    SQL(
                        name = sqlFields.getString(0),
                        sqlQuery = sqlFields.getString(1),
                        enabled = sqlFields.getBoolean(2),
                        mail
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            exitProcess(1)
        }
        return sqlList
    }
}