package br.lara.painel

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews

const val ACTION_REFRESH = "br.lara.painel.REFRESH"

object Widgets {
    private fun abrirApp(c: Context, req: Int): PendingIntent =
        PendingIntent.getActivity(
            c, req, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun atualizar(c: Context, cls: Class<*>, req: Int): PendingIntent =
        PendingIntent.getBroadcast(
            c, req, Intent(c, cls).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun id(c: Context, nome: String) = c.resources.getIdentifier(nome, "id", c.packageName)

    private fun rodape(c: Context, erro: String?): String {
        val h = Data.horaAtualizacao(c)
        return when {
            erro != null && h.isNotEmpty() -> "$erro · $h"
            erro != null -> erro
            else -> h
        }
    }

    fun renderAgenda(c: Context, erro: String?) {
        val mgr = AppWidgetManager.getInstance(c)
        val ids = mgr.getAppWidgetIds(ComponentName(c, AgendaWidget::class.java))
        if (ids.isEmpty()) return
        val evs = Data.eventos(c, 6)
        for (wid in ids) {
            val rv = RemoteViews(c.packageName, R.layout.widget_agenda)
            for (i in 0 until 6) {
                val row = id(c, "ar$i")
                if (i < evs.size) {
                    val e = evs[i]
                    rv.setViewVisibility(row, View.VISIBLE)
                    rv.setTextViewText(id(c, "ad$i"), Data.rotuloData(e))
                    rv.setTextViewText(id(c, "at$i"), e.titulo + Data.sufixoPeriodo(e))
                    try { rv.setTextColor(id(c, "ad$i"), Color.parseColor(e.cor)) } catch (_: Exception) { }
                } else rv.setViewVisibility(row, View.GONE)
            }
            rv.setViewVisibility(R.id.aempty, if (evs.isEmpty()) View.VISIBLE else View.GONE)
            rv.setTextViewText(R.id.aempty, if (!Data.hasToken(c)) "Abra o app uma vez para ativar o widget." else "Sem compromissos próximos.")
            rv.setTextViewText(R.id.afoot, rodape(c, erro))
            rv.setOnClickPendingIntent(R.id.aroot, abrirApp(c, 1))
            rv.setOnClickPendingIntent(R.id.arefresh, atualizar(c, AgendaWidget::class.java, 2))
            mgr.updateAppWidget(wid, rv)
        }
    }

    fun renderNotas(c: Context, erro: String?) {
        val mgr = AppWidgetManager.getInstance(c)
        val ids = mgr.getAppWidgetIds(ComponentName(c, NotasWidget::class.java))
        if (ids.isEmpty()) return
        val notas = Data.notas(c, 7)
        for (wid in ids) {
            val rv = RemoteViews(c.packageName, R.layout.widget_notas)
            for (i in 0 until 7) {
                val row = id(c, "nr$i")
                if (i < notas.size) {
                    val n = notas[i]
                    rv.setViewVisibility(row, View.VISIBLE)
                    rv.setTextViewText(id(c, "nd$i"), Data.nomeArea(n.area).uppercase())
                    rv.setTextViewText(id(c, "nt$i"), n.texto)
                } else rv.setViewVisibility(row, View.GONE)
            }
            rv.setViewVisibility(R.id.nempty, if (notas.isEmpty()) View.VISIBLE else View.GONE)
            rv.setTextViewText(R.id.nempty, if (!Data.hasToken(c)) "Abra o app uma vez para ativar o widget." else "Nenhuma anotação pendente.")
            rv.setTextViewText(R.id.nfoot, rodape(c, erro))
            rv.setOnClickPendingIntent(R.id.nroot, abrirApp(c, 3))
            rv.setOnClickPendingIntent(R.id.nrefresh, atualizar(c, NotasWidget::class.java, 4))
            mgr.updateAppWidget(wid, rv)
        }
    }

    fun renderTudo(c: Context, erro: String?) {
        renderAgenda(c, erro)
        renderNotas(c, erro)
    }

    /** Mostra o que está guardado, busca na nuvem e mostra de novo. Chamar fora da thread principal. */
    fun sincronizar(c: Context) {
        renderTudo(c, null)
        val erro = Data.fetch(c)
        renderTudo(c, erro)
    }
}

abstract class BaseWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action
        if (a == AppWidgetManager.ACTION_APPWIDGET_UPDATE || a == ACTION_REFRESH) {
            val pr = goAsync()
            val app = context.applicationContext
            Thread {
                try { Widgets.sincronizar(app) } finally { pr.finish() }
            }.start()
        } else super.onReceive(context, intent)
    }
}

class AgendaWidget : BaseWidget()
class NotasWidget : BaseWidget()
