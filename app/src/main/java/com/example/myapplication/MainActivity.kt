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


class MainActivity : AppCompatActivity() {
    lateinit var bienvenida: LinearLayout
    lateinit var login: LinearLayout
    lateinit var registro: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("usuarios", MODE_PRIVATE)

        bienvenida = findViewById(R.id.vistaBienvenida)
        login = findViewById(R.id.vistaLogin)
        registro = findViewById(R.id.vistaRegistro)

        // Cambiar de vista
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

        // Inicia sesion-------------------------- ------ -------
        findViewById<Button>(R.id.btnIniciar).setOnClickListener {
            val user = findViewById<EditText>(R.id.usuario).text.toString()
            val pass = findViewById<EditText>(R.id.pin).text.toString()

            val savedUser = prefs.getString("usuario", "")
            val savedPin = prefs.getString("pin", "")

            if (user == savedUser && pass == savedPin) {
                Toast.makeText(this, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, GaleriaActivity::class.java))
            } else {
                Toast.makeText(this, "Usuario o PIN incorrecto", Toast.LENGTH_SHORT).show()
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
