package br.lara.painel

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import java.util.Calendar

/** Agenda notificações locais (AlarmManager) a partir dos eventos e to-dos sincronizados. */
object Notif {
    const val CANAL = "lembretes"
    const val ACAO_AVISO = "br.lara.painel.AVISO"
    const val ACAO_SYNC = "br.lara.painel.SYNC"
    private const val PREFS = "painel"
    private const val CHAVE = "alarmes"

    private class Aviso(val codigo: Int, val quando: Long, val titulo: String, val texto: String)

    fun criarCanal(c: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CANAL, "Lembretes", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Compromissos da agenda e prazos de tarefas" }
            )
        }
    }

    private fun momento(data: String, hora: String, menosMin: Int): Long? {
        return try {
            val cal = Calendar.getInstance()
            cal.clear()
            val y = data.substring(0, 4).toInt(); val m = data.substring(5, 7).toInt() - 1; val d = data.substring(8, 10).toInt()
            val h = if (hora.length >= 5) hora.substring(0, 2).toInt() else 8
            val mi = if (hora.length >= 5) hora.substring(3, 5).toInt() else 0
            cal.set(y, m, d, h, mi, 0)
            cal.timeInMillis - menosMin * 60_000L
        } catch (_: Exception) { null }
    }

    private fun dm(data: String) = data.substring(8, 10) + "/" + data.substring(5, 7)

    private fun coletar(c: Context): List<Aviso> {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val agora = System.currentTimeMillis()
        val limite = agora + 90L * 24 * 3600 * 1000
        val out = ArrayList<Aviso>()
        try {
            val arr = JSONArray(p.getString("agdEventos", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (!o.has("lem")) continue
                val lem = o.optInt("lem", -1)
                if (lem < 0) continue
                val data = o.optString("data"); if (data.isEmpty()) continue
                val hora = o.optString("hora")
                val t = momento(data, hora, lem) ?: continue
                if (t <= agora || t > limite) continue
                val quando = if (hora.length >= 5) "${dm(data)} às ${hora.take(5)}" else dm(data)
                val obs = o.optString("obs")
                out.add(Aviso(("e" + o.optString("id") + "|" + data).hashCode(), t,
                    o.optString("titulo"), quando + (if (obs.isNotEmpty()) " · $obs" else "")))
            }
        } catch (_: Exception) { }
        try {
            val arr = JSONArray(p.getString("facTodoGeral", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optBoolean("done", false)) continue
                val data = o.optString("data"); if (data.isEmpty()) continue
                val lem = if (o.has("lem")) o.optInt("lem", 1440) else 1440
                if (lem < 0) continue
                val t = momento(data, "", lem) ?: continue
                if (t <= agora || t > limite) continue
                out.add(Aviso(("t" + o.optString("id")).hashCode(), t,
                    "Prazo: " + o.optString("nome"), "Entrega em " + dm(data)))
            }
        } catch (_: Exception) { }
        return out.sortedBy { it.quando }.take(150)
    }

    private fun pi(c: Context, a: Aviso?, codigo: Int, flags: Int = PendingIntent.FLAG_IMMUTABLE): PendingIntent {
        val i = Intent(c, AvisoReceiver::class.java).setAction(ACAO_AVISO)
        if (a != null) { i.putExtra("t", a.titulo); i.putExtra("x", a.texto); i.putExtra("n", a.codigo) }
        return PendingIntent.getBroadcast(c, codigo, i, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Cancela os alarmes antigos e agenda os atuais. */
    fun agendar(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        (p.getString(CHAVE, "") ?: "").split(",").filter { it.isNotEmpty() }.forEach {
            val cod = it.toIntOrNull() ?: return@forEach
            val ex = PendingIntent.getBroadcast(c, cod, Intent(c, AvisoReceiver::class.java).setAction(ACAO_AVISO),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if (ex != null) { am.cancel(ex); ex.cancel() }
        }
        val avisos = coletar(c)
        val exato = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        for (a in avisos) {
            val pend = pi(c, a, a.codigo, PendingIntent.FLAG_UPDATE_CURRENT)
            try {
                if (exato) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, a.quando, pend)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, a.quando, pend)
            } catch (_: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, a.quando, pend)
            }
        }
        p.edit().putString(CHAVE, avisos.joinToString(",") { it.codigo.toString() }).apply()
        // sincronização periódica em segundo plano (a cada ~3 h) para pegar eventos criados em outro aparelho
        val sync = PendingIntent.getBroadcast(c, 7, Intent(c, AvisoReceiver::class.java).setAction(ACAO_SYNC),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 3 * 3600_000L, 3 * 3600_000L, sync)
    }
}
