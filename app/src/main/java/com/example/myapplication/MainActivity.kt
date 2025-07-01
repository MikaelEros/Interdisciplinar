package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.FirebaseApp
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor
import android.content.Context


class MainActivity : AppCompatActivity() {
    lateinit var bienvenida: LinearLayout
    lateinit var login: LinearLayout
    lateinit var registro: LinearLayout
    lateinit var biometricPrompt: BiometricPrompt
    lateinit var promptInfo: BiometricPrompt.PromptInfo
    lateinit var executor: Executor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("usuarios", Context.MODE_PRIVATE)

        bienvenida = findViewById(R.id.vistaBienvenida)
        login = findViewById(R.id.vistaLogin)
        registro = findViewById(R.id.vistaRegistro)

        // Vista
        findViewById<Button>(R.id.btnContinuar).setOnClickListener {
            mostrarVista(login)
        }

        findViewById<TextView>(R.id.irRegistro).setOnClickListener {
            mostrarVista(registro)
        }

        findViewById<TextView>(R.id.irLogin).setOnClickListener {
            mostrarVista(login)
        }

        // Registro
        findViewById<Button>(R.id.btnRegistrar).setOnClickListener {
            val user = findViewById<EditText>(R.id.nuevoUsuario).text.toString()
            val pin = findViewById<EditText>(R.id.nuevoPin).text.toString()
            val confirmar = findViewById<EditText>(R.id.confirmarPin).text.toString()

            if (user.isEmpty() || pin.isEmpty() || confirmar.isEmpty()) {
                Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
            } else if (pin != confirmar) {
                Toast.makeText(this, "Los PINs no coinciden", Toast.LENGTH_SHORT).show()
            } else {
                prefs.edit().apply {
                    putString("usuario", user)
                    putString("pin", pin)
                    apply()
                }
                Toast.makeText(this, "Registro exitoso", Toast.LENGTH_SHORT).show()
                mostrarVista(login)
            }
        }

        // Configurar autenticación biométrica
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Toast.makeText(applicationContext, "Huella verificada", Toast.LENGTH_SHORT).show()
                startActivity(Intent(applicationContext, GaleriaActivity::class.java))
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Toast.makeText(applicationContext, "Error: $errString", Toast.LENGTH_SHORT).show()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Toast.makeText(applicationContext, "Autenticación fallida", Toast.LENGTH_SHORT).show()
            }
        })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verificación biométrica")
            .setSubtitle("Usa tu huella digital para acceder")
            .setNegativeButtonText("Cancelar")
            .build()

        // Iniciar sesión con huella
        findViewById<Button>(R.id.btnHuella).setOnClickListener {
            val biometricManager = BiometricManager.from(this)
            if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
                biometricPrompt.authenticate(promptInfo)
            } else {
                Toast.makeText(this, "Biometría no disponible en este dispositivo", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun mostrarVista(vista: View) {
        bienvenida.visibility = View.GONE
        login.visibility = View.GONE
        registro.visibility = View.GONE
        vista.visibility = View.VISIBLE
    }
}