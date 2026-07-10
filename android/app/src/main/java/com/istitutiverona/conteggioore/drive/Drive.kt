package com.istitutiverona.conteggioore.drive

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Client Drive minimale via REST (scope drive.file: vede solo i file creati dall'app).
// ponytail: niente SDK Drive (pesante); HttpURLConnection + org.json bastano.
object Drive {
    private const val API = "https://www.googleapis.com/drive/v3"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    const val SCOPE = "https://www.googleapis.com/auth/drive.file"

    data class FileDrive(val id: String, val nome: String, val creato: String)

    /** Token OAuth dell'account Google connesso, null se non connesso. Bloccante: chiamare su IO. */
    fun token(context: Context): String? {
        val acc = GoogleSignIn.getLastSignedInAccount(context)?.account ?: return null
        return GoogleAuthUtil.getToken(context, acc, "oauth2:$SCOPE")
    }

    fun tokenDisponibile(context: Context): Boolean =
        GoogleSignIn.getLastSignedInAccount(context) != null

    private fun req(
        url: String, token: String, metodo: String = "GET",
        corpo: ByteArray? = null, contentType: String? = null,
    ): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = metodo
        c.setRequestProperty("Authorization", "Bearer $token")
        contentType?.let { c.setRequestProperty("Content-Type", it) }
        if (corpo != null) {
            c.doOutput = true
            c.outputStream.use { it.write(corpo) }
        }
        val risposta = (if (c.responseCode < 400) c.inputStream else c.errorStream)
            ?.bufferedReader()?.readText() ?: ""
        if (c.responseCode >= 400) error("Drive HTTP ${c.responseCode}: ${risposta.take(200)}")
        return risposta
    }

    private fun cercaCartella(token: String, nome: String, parentId: String?): String? {
        var q = "name='$nome' and mimeType='application/vnd.google-apps.folder' and trashed=false"
        if (parentId != null) q += " and '$parentId' in parents"
        val r = JSONObject(req("$API/files?q=${URLEncoder.encode(q, "UTF-8")}&fields=files(id)", token))
        val files = r.getJSONArray("files")
        return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    private fun creaCartella(token: String, nome: String, parentId: String?): String {
        val meta = JSONObject().put("name", nome)
            .put("mimeType", "application/vnd.google-apps.folder")
        if (parentId != null) meta.put("parents", JSONArray().put(parentId))
        return JSONObject(
            req("$API/files", token, "POST", meta.toString().toByteArray(), "application/json")
        ).getString("id")
    }

    /** Assicura ConteggioOreAllievi/Backups, ritorna l'id della cartella Backups. */
    fun cartellaBackups(token: String): String {
        val radice = cercaCartella(token, "ConteggioOreAllievi", null)
            ?: creaCartella(token, "ConteggioOreAllievi", null)
        return cercaCartella(token, "Backups", radice) ?: creaCartella(token, "Backups", radice)
    }

    fun upload(token: String, cartellaId: String, nome: String, file: File) {
        val b = "cob-boundary"
        val meta = JSONObject().put("name", nome).put("parents", JSONArray().put(cartellaId))
        val testa = ("--$b\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n" +
            "--$b\r\nContent-Type: application/octet-stream\r\n\r\n").toByteArray()
        val coda = "\r\n--$b--".toByteArray()
        req(
            "$UPLOAD/files?uploadType=multipart", token, "POST",
            testa + file.readBytes() + coda, "multipart/related; boundary=$b"
        )
    }

    /** Backup in cartella, dal più recente. */
    fun lista(token: String, cartellaId: String): List<FileDrive> {
        val q = URLEncoder.encode("'$cartellaId' in parents and trashed=false", "UTF-8")
        val r = JSONObject(
            req("$API/files?q=$q&orderBy=createdTime desc&fields=files(id,name,createdTime)", token)
        )
        val files = r.getJSONArray("files")
        return (0 until files.length()).map {
            val f = files.getJSONObject(it)
            FileDrive(f.getString("id"), f.getString("name"), f.getString("createdTime"))
        }
    }

    fun scarica(token: String, fileId: String, dest: File) {
        val c = URL("$API/files/$fileId?alt=media").openConnection() as HttpURLConnection
        c.setRequestProperty("Authorization", "Bearer $token")
        if (c.responseCode >= 400) error("Drive HTTP ${c.responseCode}")
        c.inputStream.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
    }

    fun elimina(token: String, fileId: String) {
        req("$API/files/$fileId", token, "DELETE")
    }
}
