package br.lara.painel

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import java.util.Calendar
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews

const val ACTION_REFRESH = "br.lara.painel.REFRESH"
const val ACTION_PREV = "br.lara.painel.PREV"
const val ACTION_NEXT = "br.lara.painel.NEXT"
const val ACTION_HOJE = "br.lara.painel.HOJE"

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

    private val MESES = arrayOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho",
        "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")

    private fun acao(c: Context, acao: String, req: Int): PendingIntent =
        PendingIntent.getBroadcast(
            c, req, Intent(c, AgendaWidget::class.java).setAction(acao),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun renderAgenda(c: Context, erro: String?) {
        val mgr = AppWidgetManager.getInstance(c)
        val ids = mgr.getAppWidgetIds(ComponentName(c, AgendaWidget::class.java))
        if (ids.isEmpty()) return
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.add(Calendar.MONTH, Data.mesOffset(c))
        val ano = cal.get(Calendar.YEAR)
        val mes = cal.get(Calendar.MONTH)
        val de = CalRender.key(CalRender.inicioGrade(ano, mes))
        val ate = CalRender.key(CalRender.fimGrade(ano, mes))
        val itens = Data.itensCalendario(c, de, ate)
        val dark = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val d = c.resources.displayMetrics.density
        val land = c.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val titulo = MESES[mes] + if (ano != Calendar.getInstance().get(Calendar.YEAR)) " $ano" else ""

        for (wid in ids) {
            val o = mgr.getAppWidgetOptions(wid)
            var wDp = if (land) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            var hDp = if (land) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            if (wDp <= 0) wDp = 300
            if (hDp <= 0) hDp = 420
            var wPx = ((wDp - 28) * d).toInt()
            var hPx = ((hDp - 8 - 10 - 44 - 14) * d).toInt()
            while (wPx.toLong() * hPx * 4 > 3_000_000L) { wPx = wPx * 9 / 10; hPx = hPx * 9 / 10 }
            val bmp = CalRender.render(c, wPx, hPx, ano, mes, itens, dark)

            val rv = RemoteViews(c.packageName, R.layout.widget_agenda)
            rv.setImageViewBitmap(R.id.acal, bmp)
            rv.setTextViewText(R.id.atitle, titulo)
            rv.setTextViewText(R.id.afoot, if (!Data.hasToken(c)) "Abra o app uma vez para ativar o widget." else rodape(c, erro))
            rv.setOnClickPendingIntent(R.id.aroot, abrirApp(c, 1))
            rv.setOnClickPendingIntent(R.id.acal, abrirApp(c, 1))
            rv.setOnClickPendingIntent(R.id.aplus, abrirApp(c, 5))
            rv.setOnClickPendingIntent(R.id.aprev, acao(c, ACTION_PREV, 6))
            rv.setOnClickPendingIntent(R.id.anext, acao(c, ACTION_NEXT, 7))
            rv.setOnClickPendingIntent(R.id.atitle, acao(c, ACTION_HOJE, 8))
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
        } else if (a == ACTION_PREV || a == ACTION_NEXT || a == ACTION_HOJE) {
            val app = context.applicationContext
            val atual = Data.mesOffset(app)
            Data.setMesOffset(app, when (a) { ACTION_PREV -> atual - 1; ACTION_NEXT -> atual + 1; else -> 0 })
            val pr = goAsync()
            Thread {
                try { Widgets.renderAgenda(app, null) } finally { pr.finish() }
            }.start()
        } else super.onReceive(context, intent)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        val app = context.applicationContext
        Thread { Widgets.renderTudo(app, null) }.start()
    }
}

class AgendaWidget : BaseWidget()
class NotasWidget : BaseWidget()
