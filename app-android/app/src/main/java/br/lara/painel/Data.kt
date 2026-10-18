package br.lara.painel

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Ev(val titulo: String, val data: String, val fim: String, val hora: String, val cor: String)
data class Nota(val texto: String, val area: String, val criada: Long)

object Data {
    const val BASE = "https://meu-app-beryl-three.vercel.app"
    private const val PREFS = "painel"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveToken(c: Context, t: String?) {
        if (t.isNullOrBlank() || t == "null") return
        prefs(c).edit().putString("token", t).apply()
    }

    fun hasToken(c: Context) = !prefs(c).getString("token", null).isNullOrBlank()

    fun mesOffset(c: Context) = prefs(c).getInt("mesOffset", 0)
    fun setMesOffset(c: Context, v: Int) = prefs(c).edit().putInt("mesOffset", v).apply()

    private fun cor(s: String, padrao: Int): Int = try { android.graphics.Color.parseColor(s) } catch (_: Exception) { padrao }

    /** Eventos da agenda e to-dos marcados para a agenda, dentro do intervalo exibido. */
    fun itensCalendario(c: Context, de: String, ate: String): List<Item> {
        val out = ArrayList<Item>()
        try {
            val arr = JSONArray(prefs(c).getString("agdEventos", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val data = o.optString("data")
                if (data.isEmpty()) continue
                val fim = o.optString("fim").let { if (it.isEmpty() || it < data) data else it }
                if (fim < de || data > ate) continue
                out.add(Item(o.optString("titulo"), data, fim, cor(o.optString("cor", "#5B4ED1"), 0xFF5B4ED1.toInt())))
            }
        } catch (_: Exception) { }
        try {
            val arr = JSONArray(prefs(c).getString("facTodoGeral", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optBoolean("done", false)) continue
                if (o.has("agd") && !o.optBoolean("agd", true)) continue
                val data = o.optString("data")
                if (data.isEmpty() || data < de || data > ate) continue
                out.add(Item("\u2610 " + o.optString("nome"), data, data, 0xFFE08A1E.toInt()))
            }
        } catch (_: Exception) { }
        return out
    }

    fun lastUpdate(c: Context) = prefs(c).getLong("updated", 0L)

    /** Busca os dados na nuvem. Devolve null se deu certo ou uma mensagem de erro curta. */
    fun fetch(c: Context): String? {
        val tok = prefs(c).getString("token", null)
        if (tok.isNullOrBlank()) return "Abra o app uma vez para ativar"
        return try {
            val con = URL("$BASE/api/sync").openConnection() as HttpURLConnection
            con.requestMethod = "GET"
            con.connectTimeout = 10000
            con.readTimeout = 15000
            con.setRequestProperty("x-sync-token", tok)
            val code = con.responseCode
            if (code == 401) {
                prefs(c).edit().remove("token").apply()
                return "Senha de sincronização inválida"
            }
            if (code != 200) return "Erro $code"
            val body = con.inputStream.bufferedReader().use { it.readText() }
            val all = JSONObject(body)
            val ed = prefs(c).edit()
            for (k in listOf("agdEventos", "notas", "facTodoGeral")) {
                val o = all.optJSONObject(k)
                if (o != null) ed.putString(k, o.optString("v", "[]"))
            }
            ed.putLong("updated", System.currentTimeMillis()).apply()
            null
        } catch (e: Exception) {
            "Sem conexão"
        }
    }

    private fun fmt(cal: Calendar): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

    fun hoje(): String = fmt(Calendar.getInstance())
    fun amanha(): String { val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_MONTH, 1); return fmt(c) }

    fun eventos(c: Context, limite: Int): List<Ev> {
        val raw = prefs(c).getString("agdEventos", "[]") ?: "[]"
        val h = hoje()
        val out = ArrayList<Ev>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val data = o.optString("data")
                if (data.isEmpty()) continue
                val fim = o.optString("fim").let { if (it.isEmpty() || it < data) data else it }
                if (fim < h) continue
                out.add(Ev(o.optString("titulo"), data, fim, o.optString("hora"), o.optString("cor", "#5B4ED1")))
            }
        } catch (_: Exception) { }
        // eventos em andamento aparecem com a data de hoje
        return out.sortedWith(compareBy({ maxOf(it.data, h) }, { it.hora.ifEmpty { "99:99" } })).take(limite)
    }

    fun rotuloData(e: Ev): String {
        val h = hoje()
        val ref = if (e.data < h) h else e.data
        val base = when (ref) {
            h -> "Hoje"
            amanha() -> "Amanhã"
            else -> ref.substring(8, 10) + "/" + ref.substring(5, 7)
        }
        return if (e.hora.isNotEmpty()) base + "\n" + e.hora.take(5) else base
    }

    fun sufixoPeriodo(e: Ev): String =
        if (e.fim > e.data) "  (até " + e.fim.substring(8, 10) + "/" + e.fim.substring(5, 7) + ")" else ""

    fun notas(c: Context, limite: Int): List<Nota> {
        val raw = prefs(c).getString("notas", "[]") ?: "[]"
        val out = ArrayList<Nota>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optBoolean("feita", false)) continue
                out.add(Nota(o.optString("texto"), o.optString("area", ""), o.optLong("criada", 0L)))
            }
        } catch (_: Exception) { }
        return out.sortedByDescending { it.criada }.take(limite)
    }

    fun nomeArea(a: String) = when (a) {
        "financas" -> "Finanças"
        "treino" -> "Treino"
        "dieta" -> "Dieta"
        "casa" -> "Casa"
        "leitura" -> "Leitura"
        "agenda" -> "Agenda"
        "faculdade" -> "Faculdade"
        else -> "Geral"
    }

    fun horaAtualizacao(c: Context): String {
        val t = lastUpdate(c)
        if (t == 0L) return ""
        return "atualizado às " + SimpleDateFormat("HH:mm", Locale.US).format(Date(t))
    }
}
