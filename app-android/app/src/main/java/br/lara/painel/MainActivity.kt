package br.lara.painel

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText

class MainActivity : Activity() {
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#5B4ED1")
        web = WebView(this)
        setContentView(web)

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                val u = request.url
                if (u.toString().startsWith(Data.BASE)) return false
                startActivity(Intent(Intent.ACTION_VIEW, u))
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                guardarSenha()
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            // A página pede a senha de sincronização com window.prompt; o WebView precisa tratar isso.
            override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String?, result: JsPromptResult): Boolean {
                val campo = EditText(this@MainActivity)
                campo.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                campo.setText(defaultValue ?: "")
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setView(campo)
                    .setCancelable(false)
                    .setPositiveButton("OK") { _, _ ->
                        val t = campo.text.toString()
                        if (message.contains("Senha", ignoreCase = true)) Data.saveToken(this@MainActivity, t)
                        result.confirm(t)
                    }
                    .setNegativeButton("Cancelar") { _, _ -> result.cancel() }
                    .show()
                return true
            }

            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity).setMessage(message).setCancelable(false)
                    .setPositiveButton("OK") { _, _ -> result.confirm() }.show()
                return true
            }

            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity).setMessage(message).setCancelable(false)
                    .setPositiveButton("OK") { _, _ -> result.confirm() }
                    .setNegativeButton("Cancelar") { _, _ -> result.cancel() }.show()
                return true
            }
        }

        if (savedInstanceState != null) web.restoreState(savedInstanceState) else web.loadUrl(Data.BASE)
    }

    private fun guardarSenha() {
        web.evaluateJavascript("(function(){return localStorage.getItem('syncToken');})()") { v ->
            if (v != null && v != "null") Data.saveToken(this, v.trim('"'))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onStop() {
        super.onStop()
        guardarSenha()
        // dá tempo de a página enviar as alterações e depois atualiza os widgets
        val app = applicationContext
        Thread {
            try { Thread.sleep(4000) } catch (_: InterruptedException) { }
            Widgets.sincronizar(app)
        }.start()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
