package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ConfiguracionActivity : AppCompatActivity() {

    private lateinit var radioGroupAuth: RadioGroup
    private lateinit var radioHuella: RadioButton
    private lateinit var radioPin: RadioButton
    private lateinit var checkCifrado: CheckBox
    private lateinit var checkPinExtra: CheckBox
    private lateinit var btnCambiarContrasena: Button
    private lateinit var btnCambiarCorreo: Button
    private lateinit var btnEliminarCuenta: Button
    private lateinit var btnCerrarSesion: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.actividad_configuracion)

        // Referenciar elementos del layout
        radioGroupAuth = findViewById(R.id.radioGroupAuth)
        radioHuella = findViewById(R.id.radioHuella)
        radioPin = findViewById(R.id.radioPin)
        checkCifrado = findViewById(R.id.checkCifrado)
        checkPinExtra = findViewById(R.id.checkPinExtra)
        btnCambiarContrasena = findViewById(R.id.btnCambiarContrasena)
        btnCambiarCorreo = findViewById(R.id.btnCambiarCorreo)
        btnEliminarCuenta = findViewById(R.id.btnEliminarCuenta)
        btnCerrarSesion = findViewById(R.id.btnCerrarSesion)

        val prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE)
        val metodoGuardado = prefs.getString("auth_method", "huella") // huella por defecto

        if (metodoGuardado == "huella") {
            radioHuella.isChecked = true
        } else {
            radioPin.isChecked = true
        }

        // Listeners de botones (a completar con lógica real)
        btnCambiarContrasena.setOnClickListener {
            Toast.makeText(this, "Función de cambio de contraseña", Toast.LENGTH_SHORT).show()
        }

        btnCambiarCorreo.setOnClickListener {
            Toast.makeText(this, "Función de cambio de correo", Toast.LENGTH_SHORT).show()
        }

        btnEliminarCuenta.setOnClickListener {
            Toast.makeText(this, "Cuenta eliminada (simulado)", Toast.LENGTH_SHORT).show()
        }

        btnCerrarSesion.setOnClickListener {
            Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show()
            // Puedes agregar redirección a LoginActivity
            // startActivity(Intent(this, LoginActivity::class.java))
            // finish()
        }

        radioGroupAuth.setOnCheckedChangeListener { _, checkedId ->
            val prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE)
            val editor = prefs.edit()

            when (checkedId) {
                R.id.radioHuella -> {
                    editor.putString("auth_method", "huella")
                    Toast.makeText(this, "Autenticación por huella activada", Toast.LENGTH_SHORT).show()
                }
                R.id.radioPin -> {
                    editor.putString("auth_method", "pin")
                    Toast.makeText(this, "Autenticación por PIN activada", Toast.LENGTH_SHORT).show()
                }
            }

            editor.apply()
        }


        // Opcional: puedes guardar las opciones elegidas en SharedPreferences
    }

}
