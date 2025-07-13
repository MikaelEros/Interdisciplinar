package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import android.widget.EditText
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
import android.content.SharedPreferences
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date
import android.util.Log
import java.security.MessageDigest
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.IntentFilter
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkRequest

// Custom error handler for better logging
object AppLogger {
    private const val TAG = "RecoveryGallery"
    private var isDebugMode = true // Set to false for production
    
    fun d(message: String) {
        if (isDebugMode) {
            Log.d(TAG, message)
        }
    }
    
    fun e(message: String, throwable: Throwable? = null) {
        if (isDebugMode) {
            if (throwable != null) {
                Log.e(TAG, message, throwable)
            } else {
                Log.e(TAG, message)
            }
        }
    }
    
    fun w(message: String) {
        if (isDebugMode) {
            Log.w(TAG, message)
        }
    }
}

class MainActivity : AppCompatActivity() {
    lateinit var bienvenida: LinearLayout
    lateinit var login: LinearLayout
    lateinit var registro: LinearLayout
    lateinit var biometricPrompt: BiometricPrompt
    lateinit var promptInfo: BiometricPrompt.PromptInfo
    lateinit var executor: Executor
    lateinit var userSpecificBiometric: UserSpecificBiometric

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            AppLogger.d("Iniciando MainActivity")
            
            // Inicializar Firebase
            com.google.firebase.FirebaseApp.initializeApp(this)
            AppLogger.d("Firebase inicializado")
            
            // Inicializar sistema de biometría específica por usuario
            userSpecificBiometric = UserSpecificBiometric(this)
            
            setContentView(R.layout.activity_main)
            AppLogger.d("Layout cargado")

            // Inicializar SharedPreferences de forma más segura
            val prefs = getSharedPreferences("RecoveryGallery", Context.MODE_PRIVATE)

            bienvenida = findViewById(R.id.vistaBienvenida)
            login = findViewById(R.id.vistaLogin)
            registro = findViewById(R.id.vistaRegistro)
            
            AppLogger.d("Vistas encontradas")

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
            
            // Configurar listener para el campo de email en login
            findViewById<EditText>(R.id.usuario).addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val email = s?.toString() ?: ""
                    if (email.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        // Eliminar mensajes y lógica de huella específica por usuario
                    } else {
                        // Email inválido o vacío, ocultar botón de huella
                        findViewById<Button>(R.id.btnHuella).visibility = View.GONE
                    }
                }
            })
            
            // Configurar checkbox de huella como obligatorio
            val checkHuella = findViewById<CheckBox>(R.id.checkHuella)
            checkHuella.isChecked = true // Marcado por defecto
            checkHuella.isEnabled = false // No se puede desmarcar
            checkHuella.text = "Huella digital (Obligatorio)"
            
            // Configurar botón de login normal
            findViewById<Button>(R.id.btnLogin).setOnClickListener {
                val email = findViewById<EditText>(R.id.usuario).text.toString()
                val password = findViewById<EditText>(R.id.pin).text.toString()
                
                if (email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(this, "Por favor ingresa un email válido", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                AppLogger.d("Iniciando login normal para: $email")
                Toast.makeText(this, "Iniciando sesión...", Toast.LENGTH_SHORT).show()
                
                // Login normal con Firebase
                FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            AppLogger.d("Login normal exitoso")
                            guardarDatosUsuario(email, true)
                            registrarActividadLogin(email)
                            startActivity(Intent(this, GaleriaActivity::class.java))
                            finish()
                        } else {
                            val errorMsg = task.exception?.localizedMessage ?: "Error desconocido"
                            AppLogger.e("Error en login normal: $errorMsg")
                            Toast.makeText(this, "Error de autenticación: $errorMsg", Toast.LENGTH_LONG).show()
                        }
                    }
            }
            
            // Cambiar texto del botón de huella
            findViewById<Button>(R.id.btnHuella).text = "Iniciar Sesión con Huella"
            
            AppLogger.d("Botones de navegación configurados")

            // Registro
            findViewById<Button>(R.id.btnRegistrar).setOnClickListener {
                AppLogger.d("Botón registrar presionado")
                val user = findViewById<EditText>(R.id.nuevoUsuario).text.toString()
                val password = findViewById<EditText>(R.id.nuevoPin).text.toString()
                val confirmar = findViewById<EditText>(R.id.confirmarPin).text.toString()
                val huellaActiva = findViewById<CheckBox>(R.id.checkHuella).isChecked
                val pregunta = findViewById<EditText>(R.id.editPreguntaRegistro).text.toString()
                val respuesta = findViewById<EditText>(R.id.editRespuestaRegistro).text.toString()

                AppLogger.d("Validando campos: user=$user, password=${password.length} chars")

                if (user.isEmpty() || password.isEmpty() || confirmar.isEmpty() || pregunta.isEmpty() || respuesta.isEmpty()) {
                    Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
                } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(user).matches()) {
                    Toast.makeText(this, "Por favor ingresa un email válido", Toast.LENGTH_SHORT).show()
                } else if (password.length < 6) {
                    Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
                } else if (!esContraseñaValida(password)) {
                    Toast.makeText(this, "La contraseña debe contener letras y números", Toast.LENGTH_SHORT).show()
                } else if (password != confirmar) {
                    Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                } else if (!huellaActiva) {
                    Toast.makeText(this, "Debes habilitar la huella digital para usar esta aplicación", Toast.LENGTH_LONG).show()
                } else {
                    AppLogger.d("Campos válidos, iniciando verificación de email")
                    Toast.makeText(this, "Verificando email...", Toast.LENGTH_SHORT).show()
                    
                    checkNetworkBeforeFirebaseOperation {
                        FirebaseAuth.getInstance().fetchSignInMethodsForEmail(user)
                            .addOnCompleteListener { fetchTask ->
                                AppLogger.d("Resultado de fetchSignInMethodsForEmail: ${fetchTask.isSuccessful}")
                                if (fetchTask.isSuccessful) {
                                    val signInMethods = fetchTask.result?.signInMethods
                                    AppLogger.d("SignInMethods encontrados: $signInMethods")
                                    if (signInMethods != null && signInMethods.isNotEmpty()) {
                                        AppLogger.e("Email ya existe, métodos de inicio de sesión: $signInMethods")
                                        Toast.makeText(this, "Este email ya está registrado. Usa otro email o inicia sesión.", Toast.LENGTH_LONG).show()
                                        AppLogger.d("Proceso de registro detenido - email duplicado")
                                        return@addOnCompleteListener
                                    } else {
                                        AppLogger.d("Email no existe, procediendo con el registro")
                                        Toast.makeText(this, "Registrando usuario...", Toast.LENGTH_SHORT).show()
                                        FirebaseAuth.getInstance().createUserWithEmailAndPassword(user, password)
                                            .addOnCompleteListener { task ->
                                                if (task.isSuccessful) {
                                                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                                                    if (uid != null) {
                                                        // Generar la clave de 360 bits
                                                        val clave360 = FingerprintEncryption(this).generateUserSpecific360Bits(uid)
                                                        // Cifrar la clave con la respuesta de seguridad
                                                        val clave360Cifrada = BackupUtils.cifrarClave360Bits(clave360, respuesta)
                                                        // Guardar el backup en Firestore
                                                        BackupUtils.guardarBackupClave360(uid, pregunta, clave360Cifrada)
                                                    }
                                                    // Continuar con el flujo normal de registro
                                                    guardarDatosUsuario(user, huellaActiva)
                                                    generarClaveMaestraKeystore(user)
                                                    val passwordEncriptada = encriptarPassword(password)
                                                    guardarEnFirestore(user, passwordEncriptada, huellaActiva, password)
                                                } else {
                                                    val errorMsg = task.exception?.localizedMessage ?: "Error desconocido en Auth"
                                                    AppLogger.e("Error en Firebase Auth: $errorMsg")
                                                    AppLogger.e("Tipo de excepción: ${task.exception?.javaClass?.simpleName}")
                                                    when (task.exception) {
                                                        is com.google.firebase.auth.FirebaseAuthWeakPasswordException -> {
                                                            Toast.makeText(this, "La contraseña es muy débil", Toast.LENGTH_LONG).show()
                                                        }
                                                        is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> {
                                                            Toast.makeText(this, "Email inválido", Toast.LENGTH_LONG).show()
                                                        }
                                                        is com.google.firebase.auth.FirebaseAuthUserCollisionException -> {
                                                            Toast.makeText(this, "Este email ya está registrado. Usa otro email o inicia sesión.", Toast.LENGTH_LONG).show()
                                                            AppLogger.e("Email duplicado detectado en Auth: $user")
                                                        }
                                                        else -> {
                                                            if (errorMsg.contains("already in use") || errorMsg.contains("already exists") || errorMsg.contains("duplicate")) {
                                                                Toast.makeText(this, "Este email ya está registrado. Usa otro email o inicia sesión.", Toast.LENGTH_LONG).show()
                                                                AppLogger.e("Email duplicado detectado por mensaje: $user - $errorMsg")
                                                            } else {
                                                                Toast.makeText(this, "Error en autenticación: $errorMsg", Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                    }
                                } else {
                                    AppLogger.e("Error al verificar email: ${fetchTask.exception?.message}")
                                    AppLogger.e("Stack trace: ${fetchTask.exception?.stackTraceToString()}")
                                    Toast.makeText(this, "Error al verificar email: ${fetchTask.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                                    AppLogger.d("Proceso de registro detenido - error en verificación")
                                }
                            }
                    }
                }
            }

            // Login con huella
            findViewById<Button>(R.id.btnHuella).setOnClickListener {
                AppLogger.d("Botón de huella presionado")
                val email = findViewById<EditText>(R.id.usuario).text.toString()
                val password = findViewById<EditText>(R.id.pin).text.toString()

                if (email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(this, "Por favor ingresa un email válido", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                AppLogger.d("Iniciando autenticación biométrica para: $email")
                Toast.makeText(this, "Verificando huella digital...", Toast.LENGTH_SHORT).show()

                // Iniciar autenticación biométrica
                iniciarAutenticacionBiometrica(email, password)
            }

            // Mostrar siempre la pantalla de bienvenida al abrir la app
            mostrarVista(bienvenida)
            
            findViewById<Button>(R.id.btnBackupLogin).setOnClickListener {
                AppLogger.d("Botón de backup presionado - solicitando credenciales")
                mostrarDialogoCredencialesBackup()
            }
            
        } catch (e: Exception) {
            AppLogger.e("Error inesperado en onCreate: ${e.message}", e)
            Toast.makeText(this, "Error al iniciar la aplicación: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            // Fallback: mostrar la vista de bienvenida o un mensaje de error más general
            try {
                setContentView(R.layout.activity_main)
                mostrarVista(bienvenida)
            } catch (e2: Exception) {
                AppLogger.e("Error crítico al mostrar fallback: ${e2.message}", e2)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources
        try {
            // Clear any cached data
            System.gc()
            AppLogger.d("MainActivity destroyed, resources cleaned up")
        } catch (e: Exception) {
            AppLogger.e("Error during cleanup: ${e.message}")
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        AppLogger.w("Low memory detected, cleaning up resources")
        // Clear any non-essential cached data
        System.gc()
    }

    // Network connectivity checking
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }

    private fun checkNetworkBeforeFirebaseOperation(operation: () -> Unit) {
        if (isNetworkAvailable()) {
            operation()
        } else {
            AppLogger.w("No network connection available")
            Toast.makeText(this, "Sin conexión a internet. Verifica tu conexión.", Toast.LENGTH_LONG).show()
        }
    }

    private fun mostrarVista(vista: View) {
        bienvenida.visibility = View.GONE
        login.visibility = View.GONE
        registro.visibility = View.GONE
        vista.visibility = View.VISIBLE
    }

    // Función para validar la contraseña ingresada por el usuario
    private fun esContraseñaValida(password: String): Boolean {
        if (password.length < 6) return false
        
        val tieneLetra = password.any { it.isLetter() }
        val tieneNumero = password.any { it.isDigit() }
        
        return tieneLetra && tieneNumero
    }

    // Utilidad para encriptar la contraseña con clave interna
    private fun encriptarPassword(password: String): String {
        val secretKey = "ClaveFijaParaContraseña1234567890123456".take(16).toByteArray()
        val keySpec = SecretKeySpec(secretKey, "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        val encrypted = cipher.doFinal(password.toByteArray())
        return Base64.encodeToString(encrypted, Base64.DEFAULT)
    }

    // Utilidad para generar clave maestra biométrica en el Keystore
    private fun generarClaveMaestraKeystore(usuario: String) {
        val alias = "clave_$usuario"
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (!keyStore.containsAlias(alias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val parameterSpec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(-1)
                .build()
            keyGenerator.init(parameterSpec)
            keyGenerator.generateKey()
        }
    }

    // Funciones para manejar SharedPreferences de forma segura
    private fun guardarDatosUsuario(email: String, huellaActiva: Boolean) {
        val prefs = getSharedPreferences("RecoveryGallery", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("email", email)
            .putBoolean("huella_activa", huellaActiva)
            .putLong("ultimo_acceso", System.currentTimeMillis())
            .apply()
        AppLogger.d("Datos de usuario guardados en SharedPreferences")
    }

    private fun limpiarDatosUsuario() {
        val prefs = getSharedPreferences("RecoveryGallery", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        AppLogger.d("Datos de usuario limpiados de SharedPreferences")
    }

    private fun obtenerEmailGuardado(): String? {
        val prefs = getSharedPreferences("RecoveryGallery", Context.MODE_PRIVATE)
        return prefs.getString("email", null)
    }

    private fun estaHuellaActiva(): Boolean {
        val prefs = getSharedPreferences("RecoveryGallery", Context.MODE_PRIVATE)
        return prefs.getBoolean("huella_activa", false)
    }

    // Función para verificar y actualizar la UI según la preferencia de huella del usuario
    private fun verificarPreferenciaHuella(email: String) {
        FirebaseFirestore.getInstance().collection("usuarios")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents[0]
                    val huellaActiva = document.getBoolean("huellaActiva") ?: false
                    AppLogger.d("Preferencia de huella para $email: $huellaActiva")
                    
                    val btnHuella = findViewById<Button>(R.id.btnHuella)
                    if (huellaActiva) {
                        btnHuella.visibility = View.VISIBLE
                        btnHuella.text = "Iniciar sesión con huella"
                    } else {
                        btnHuella.visibility = View.GONE
                    }
                } else {
                    AppLogger.d("Usuario no encontrado en Firestore")
                    findViewById<Button>(R.id.btnHuella).visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                AppLogger.e("Error al verificar preferencia de huella: ${e.message}")
                findViewById<Button>(R.id.btnHuella).visibility = View.GONE
            }
    }

    // Función para mostrar diálogo preguntando si quiere iniciar sesión automáticamente
    private fun mostrarDialogoInicioSesion(email: String, password: String) {
        AppLogger.d("=== INICIO mostrarDialogoInicioSesion ===")
        AppLogger.d("Email recibido: $email")
        AppLogger.d("Password recibido: ${password.take(3)}***")
        
        Toast.makeText(this, "Mostrando diálogo...", Toast.LENGTH_SHORT).show()
        
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Registro Exitoso")
        builder.setMessage("¿Quieres iniciar sesión automáticamente y ir a la galería?")
        
        builder.setPositiveButton("Sí, iniciar sesión") { dialog, which ->
            AppLogger.d("Usuario eligió iniciar sesión automáticamente")
            Toast.makeText(this, "Iniciando sesión automática...", Toast.LENGTH_SHORT).show()
            iniciarSesionAutomatica(email, password)
        }
        
        builder.setNegativeButton("No, ir al login") { dialog, which ->
            AppLogger.d("Usuario eligió ir al login")
            Toast.makeText(this, "Yendo al login...", Toast.LENGTH_SHORT).show()
            mostrarVista(login)
        }
        
        try {
            AppLogger.d("Creando y mostrando diálogo")
            val dialog = builder.create()
            dialog.show()
            AppLogger.d("=== DIÁLOGO MOSTRADO EXITOSAMENTE ===")
            Toast.makeText(this, "Diálogo mostrado", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            AppLogger.e("Error al mostrar diálogo: ${e.message}")
            AppLogger.e("Stack trace: ${e.stackTraceToString()}")
            Toast.makeText(this, "Error al mostrar diálogo: ${e.message}", Toast.LENGTH_LONG).show()
            mostrarVista(login)
        }
    }

    // Función para iniciar sesión automáticamente después del registro
    private fun iniciarSesionAutomatica(email: String, password: String) {
        AppLogger.d("Iniciando sesión automática para: $email")
        
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    AppLogger.d("Firebase Auth exitoso, guardando datos locales")
                    guardarDatosUsuario(email, true)
                    
                    val passwordEncriptada = encriptarPassword(password)
                    AppLogger.d("Password encriptada generada correctamente")
                    
                    AppLogger.d("Guardando datos en Firestore")
                    guardarEnFirestore(email, passwordEncriptada, true, password)
                } else {
                    val errorMsg = task.exception?.localizedMessage ?: "Error desconocido en Auth"
                    AppLogger.e("Error en Firebase Auth: $errorMsg")
                    AppLogger.e("Tipo de excepción: ${task.exception?.javaClass?.simpleName}")
                    
                    when (task.exception) {
                        is com.google.firebase.auth.FirebaseAuthWeakPasswordException -> {
                            Toast.makeText(this, "La contraseña es muy débil", Toast.LENGTH_LONG).show()
                        }
                        is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> {
                            Toast.makeText(this, "Email o contraseña incorrectos", Toast.LENGTH_LONG).show()
                        }
                        is com.google.firebase.auth.FirebaseAuthUserCollisionException -> {
                            Toast.makeText(this, "Este email ya está registrado. Usa otro email o inicia sesión.", Toast.LENGTH_LONG).show()
                            AppLogger.e("Email duplicado detectado en Auth: $email")
                        }
                        else -> {
                            if (errorMsg.contains("already in use") || errorMsg.contains("already exists") || errorMsg.contains("duplicate")) {
                                Toast.makeText(this, "Este email ya está registrado. Usa otro email o inicia sesión.", Toast.LENGTH_LONG).show()
                                AppLogger.e("Email duplicado detectado por mensaje: $email - $errorMsg")
                            } else {
                                Toast.makeText(this, "Error en autenticación: $errorMsg", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
    }

    private fun guardarEnFirestore(email: String, passwordEncriptada: String, huellaActiva: Boolean, passwordOriginal: String) {
        AppLogger.d("Iniciando guardado en Firestore")
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            AppLogger.d("UID obtenido: $uid")
            
            val perfil = hashMapOf(
                "uid" to uid,
                "email" to email,
                "huellaActiva" to huellaActiva,
                "biometricoObligatorio" to true,
                "permisosEncriptacion" to true,
                "estadoCuenta" to "activa",
                "fechaRegistro" to com.google.firebase.Timestamp.now(),
                "ultimaActualizacion" to com.google.firebase.Timestamp.now()
            )
            
            FirebaseFirestore.getInstance().collection("usuarios")
                .document(uid)
                .set(perfil)
                .addOnSuccessListener {
                    AppLogger.d("Perfil guardado en Firestore exitosamente")
                    
                    val datosUsuario = hashMapOf(
                        "uid" to uid,
                        "email" to email,
                        "passwordEncriptada" to passwordEncriptada,
                        "huellaActiva" to huellaActiva,
                        "fechaCreacion" to com.google.firebase.Timestamp.now(),
                        "ultimaActualizacion" to com.google.firebase.Timestamp.now()
                    )
                    
                    FirebaseFirestore.getInstance().collection("Usuarios App")
                        .document(uid)
                        .set(datosUsuario)
                        .addOnSuccessListener {
                            AppLogger.d("Datos de autenticación guardados exitosamente")
                            
                            val actividad = hashMapOf(
                                "uid" to uid,
                                "email" to email,
                                "tipoActividad" to "registro",
                                "fecha" to com.google.firebase.Timestamp.now(),
                                "detalles" to "Usuario registrado exitosamente"
                            )
                            
                            FirebaseFirestore.getInstance().collection("actividades")
                                .add(actividad)
                                .addOnSuccessListener { documentReference ->
                                    AppLogger.d("Actividad registrada: ${documentReference.id}")
                                    
                                    findViewById<EditText>(R.id.nuevoUsuario).text.clear()
                                    findViewById<EditText>(R.id.nuevoPin).text.clear()
                                    findViewById<EditText>(R.id.confirmarPin).text.clear()
                                    findViewById<EditText>(R.id.editPreguntaRegistro).text.clear()
                                    findViewById<EditText>(R.id.editRespuestaRegistro).text.clear()
                                    findViewById<CheckBox>(R.id.checkHuella).isChecked = true
                                    
                                    AppLogger.d("Registro exitoso, llamando a mostrarDialogoInicioSesion")
                                    AppLogger.d("Email: $email, Password: ${passwordOriginal.take(3)}***")
                                    AppLogger.d("=== ANTES DE LLAMAR mostrarDialogoInicioSesion ===")
                                    mostrarDialogoInicioSesion(email, passwordOriginal)
                                    AppLogger.d("=== DESPUÉS DE LLAMAR mostrarDialogoInicioSesion ===")
                                }
                                .addOnFailureListener { e ->
                                    AppLogger.e("Error al registrar actividad: ${e.message}")
                                    Toast.makeText(this, "¡Registro exitoso!", Toast.LENGTH_LONG).show()
                                    mostrarDialogoInicioSesion(email, passwordOriginal)
                                }
                        }
                        .addOnFailureListener { e ->
                            AppLogger.e("Error al guardar usuario en 'Usuarios App': ", e)
                            AppLogger.e("Stack trace: ${e.stackTraceToString()}")
                            Toast.makeText(this, "Error Firestore (Usuarios App): ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            mostrarVista(login)
                        }
                }
                .addOnFailureListener { e ->
                    AppLogger.e("Error al guardar perfil en Firestore: ", e)
                    AppLogger.e("Stack trace: ${e.stackTraceToString()}")
                    Toast.makeText(this, "Error Firestore (usuarios): ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    mostrarVista(login)
                }
        } else {
            AppLogger.e("UID nulo tras registro en Auth")
            Toast.makeText(this, "Error: UID nulo tras registro en Auth", Toast.LENGTH_LONG).show()
            mostrarVista(login)
        }
    }

    // Función para verificar si hay una sesión activa al abrir la app
    private fun verificarSesionActiva() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            AppLogger.d("Sesión de Firebase Auth activa, yendo a galería")
            startActivity(Intent(this, GaleriaActivity::class.java))
            finish()
        } else {
            AppLogger.d("No hay sesión activa, mostrando vista de bienvenida")
            mostrarVista(bienvenida)
        }
    }

    // Función para mostrar diálogo de credenciales para backup
    private fun mostrarDialogoCredencialesBackup() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Acceso al Backup")
        builder.setMessage("Ingresa tus credenciales para acceder al backup de recuperación")
        
        // Crear layout para los inputs
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 20, 50, 20)
        
        val emailInput = EditText(this)
        emailInput.hint = "Email"
        emailInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        layout.addView(emailInput)
        
        val passwordInput = EditText(this)
        passwordInput.hint = "Contraseña"
        passwordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        layout.addView(passwordInput)
        
        builder.setView(layout)
        
        builder.setPositiveButton("Acceder") { dialog, which ->
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Debes completar todos los campos", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Ingresa un email válido", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            
            AppLogger.d("Verificando credenciales para backup: $email")
            verificarCredencialesBackup(email, password)
        }
        
        builder.setNegativeButton("Cancelar") { dialog, which ->
            dialog.dismiss()
        }
        
        val dialog = builder.create()
        dialog.show()
    }

    // Función para verificar credenciales con Firebase
    private fun verificarCredencialesBackup(email: String, password: String) {
        Toast.makeText(this, "Verificando credenciales...", Toast.LENGTH_SHORT).show()
        
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = task.result?.user
                    if (user != null) {
                        AppLogger.d("Credenciales correctas para backup: ${user.email}")
                        Toast.makeText(this, "Credenciales verificadas", Toast.LENGTH_SHORT).show()
                        verificarBackupExistente(user.uid)
                    } else {
                        AppLogger.e("Usuario nulo después de autenticación exitosa")
                        Toast.makeText(this, "Error interno", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val errorMsg = task.exception?.localizedMessage ?: "Error desconocido"
                    AppLogger.e("Error en autenticación para backup: $errorMsg")
                    Toast.makeText(this, "Credenciales incorrectas: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
    }

    // Función para verificar si existe un backup y manejar el acceso
    private fun verificarBackupExistente(uid: String) {
        AppLogger.d("Verificando si existe backup para UID: $uid")
        
        FirebaseFirestore.getInstance()
            .collection("backups_clave360")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // Backup existe, mostrar pregunta de seguridad
                    val pregunta = document.getString("pregunta") ?: "Sin pregunta configurada"
                    AppLogger.d("Backup encontrado, mostrando pregunta de seguridad")
                    mostrarDialogoPreguntaSeguridad(pregunta, uid)
                } else {
                    // No hay backup, ir a crear uno nuevo
                    AppLogger.d("No se encontró backup, yendo a crear uno nuevo")
                    val intent = Intent(this, BackupClaveActivity::class.java)
                    startActivity(intent)
                }
            }
            .addOnFailureListener { e ->
                AppLogger.e("Error al verificar backup: ${e.message}")
                Toast.makeText(this, "Error al verificar backup: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    // Función para mostrar diálogo de pregunta de seguridad
    private fun mostrarDialogoPreguntaSeguridad(pregunta: String, uid: String) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Acceso al Backup")
        builder.setMessage("Ya tienes un backup configurado.\n\nPregunta de seguridad:\n$pregunta")
        
        // Crear input para la respuesta
        val input = EditText(this)
        input.hint = "Ingresa tu respuesta de seguridad"
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        builder.setView(input)
        
        builder.setPositiveButton("Acceder") { dialog, which ->
            val respuesta = input.text.toString().trim()
            if (respuesta.isEmpty()) {
                Toast.makeText(this, "Debes ingresar una respuesta", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            
            AppLogger.d("Verificando respuesta de seguridad")
            verificarRespuestaSeguridad(uid, respuesta)
        }
        
        builder.setNegativeButton("Cancelar") { dialog, which ->
            dialog.dismiss()
        }
        
        builder.setNeutralButton("Crear Nuevo Backup") { dialog, which ->
            AppLogger.d("Usuario eligió crear nuevo backup")
            val intent = Intent(this, BackupClaveActivity::class.java)
            startActivity(intent)
        }
        
        val dialog = builder.create()
        dialog.show()
    }

    // Función para verificar la respuesta de seguridad
    private fun verificarRespuestaSeguridad(uid: String, respuesta: String) {
        FirebaseFirestore.getInstance()
            .collection("backups_clave360")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val clave360Cifrada = document.getString("clave360Cifrada")
                    if (clave360Cifrada != null) {
                        try {
                            // Intentar descifrar la clave con la respuesta
                            val clave360Descifrada = BackupUtils.descifrarClave360Bits(clave360Cifrada, respuesta)
                            if (clave360Descifrada != null) {
                                AppLogger.d("Respuesta correcta, mostrando clave")
                                mostrarClave360Bits(clave360Descifrada)
                            } else {
                                AppLogger.d("Respuesta incorrecta")
                                Toast.makeText(this, "Respuesta incorrecta", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            AppLogger.e("Error al descifrar clave: ${e.message}")
                            Toast.makeText(this, "Error al verificar respuesta: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        AppLogger.e("No se encontró clave cifrada en el backup")
                        Toast.makeText(this, "Error: Backup corrupto", Toast.LENGTH_LONG).show()
                    }
                } else {
                    AppLogger.e("Backup no encontrado")
                    Toast.makeText(this, "Error: Backup no encontrado", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                AppLogger.e("Error al verificar respuesta: ${e.message}")
                Toast.makeText(this, "Error al verificar respuesta: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    // Función para mostrar la clave de 360 bits
    private fun mostrarClave360Bits(clave360: String) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Tu Clave de 360 Bits")
        builder.setMessage("Clave de recuperación:\n\n$clave360\n\n⚠️ Guarda esta clave en un lugar seguro")
        
        builder.setPositiveButton("Copiar") { dialog, which ->
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Clave 360 bits", clave360)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Clave copiada al portapapeles", Toast.LENGTH_SHORT).show()
        }
        
        builder.setNegativeButton("Cerrar") { dialog, which ->
            dialog.dismiss()
        }
        
        val dialog = builder.create()
        dialog.show()
    }

    // Función para validar usuario en Firebase
    private fun validarUsuarioEnFirebase(email: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        FirebaseFirestore.getInstance().collection("usuarios")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents[0]
                    val uid = document.getString("uid") ?: ""
                    val huellaActiva = document.getBoolean("huellaActiva") ?: false
                    val biometricoObligatorio = document.getBoolean("biometricoObligatorio") ?: false
                    val permisosEncriptacion = document.getBoolean("permisosEncriptacion") ?: false
                    val estadoCuenta = document.getString("estadoCuenta") ?: "inactiva"

                    AppLogger.d("Validando usuario: $email")
                    AppLogger.d("Huella Activa: $huellaActiva")
                    AppLogger.d("Biometrico Obligatorio: $biometricoObligatorio")
                    AppLogger.d("Permisos de Encriptacion: $permisosEncriptacion")
                    AppLogger.d("Estado de Cuenta: $estadoCuenta")

                    if (huellaActiva && biometricoObligatorio && permisosEncriptacion && estadoCuenta == "activa") {
                        onSuccess()
                    } else {
                        AppLogger.d("Usuario no cumple requisitos, intentando actualizar...")
                        actualizarUsuarioExistente(uid, email, huellaActiva, biometricoObligatorio, permisosEncriptacion, estadoCuenta)
                    }
                } else {
                    Toast.makeText(this, "Usuario no encontrado. Verifica tu email.", Toast.LENGTH_SHORT).show()
                    AppLogger.e("Usuario no encontrado en Firestore: $email")
                    mostrarVista(login)
                }
            }
            .addOnFailureListener { e ->
                AppLogger.e("Error al validar usuario en Firestore: ${e.message}")
                Toast.makeText(this, "Error al validar usuario: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                mostrarVista(login)
            }
    }

    private fun actualizarUsuarioExistente(uid: String, email: String, huellaActiva: Boolean, biometricoObligatorio: Boolean, permisosEncriptacion: Boolean, estadoCuenta: String) {
        val actualizaciones = hashMapOf<String, Any>(
            "huellaActiva" to true,
            "biometricoObligatorio" to true,
            "permisosEncriptacion" to true,
            "estadoCuenta" to "activa",
            "ultimaActualizacion" to com.google.firebase.Timestamp.now()
        )
        
        FirebaseFirestore.getInstance().collection("usuarios")
            .document(uid)
            .update(actualizaciones)
            .addOnSuccessListener {
                AppLogger.d("Usuario actualizado exitosamente")
                Toast.makeText(this, "Usuario actualizado exitosamente", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                AppLogger.e("Error al actualizar usuario: ${e.message}")
                Toast.makeText(this, "Error al actualizar usuario. Contacta al administrador.", Toast.LENGTH_LONG).show()
                mostrarVista(login)
            }
    }

    private fun registrarActividadLogin(email: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            val actividad = hashMapOf(
                "uid" to uid,
                "email" to email,
                "tipoActividad" to "login",
                "fecha" to com.google.firebase.Timestamp.now(),
                "detalles" to "Inicio de sesión exitoso"
            )
            
            FirebaseFirestore.getInstance().collection("actividades")
                .add(actividad)
                .addOnSuccessListener { documentReference ->
                    AppLogger.d("Actividad de login registrada: ${documentReference.id}")
                }
                .addOnFailureListener { e ->
                    AppLogger.e("Error al registrar actividad de login: ${e.message}")
                }
        }
    }

    // Función para iniciar autenticación biométrica
    private fun iniciarAutenticacionBiometrica(email: String, password: String) {
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    AppLogger.e("Error de autenticación biométrica: $errString")
                    Toast.makeText(this@MainActivity, "Error de autenticación: $errString", Toast.LENGTH_LONG).show()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    AppLogger.d("Autenticación biométrica exitosa")
                    
                    // Verificar usuario en Firebase
                    validarUsuarioEnFirebase(email,
                        onSuccess = {
                            // Usuario válido, proceder con login
                            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        AppLogger.d("Login exitoso después de autenticación biométrica")
                                        guardarDatosUsuario(email, true)
                                        registrarActividadLogin(email)
                                        startActivity(Intent(this@MainActivity, GaleriaActivity::class.java))
                                        finish()
                                    } else {
                                        AppLogger.e("Error en login después de autenticación biométrica: ${task.exception?.message}")
                                        Toast.makeText(this@MainActivity, "Error en login: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                        },
                        onError = { errorMessage ->
                            AppLogger.e("Error validando usuario: $errorMessage")
                            Toast.makeText(this@MainActivity, "Error validando usuario: $errorMessage", Toast.LENGTH_LONG).show()
                        }
                    )
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    AppLogger.e("Autenticación biométrica fallida")
                    Toast.makeText(this@MainActivity, "Autenticación fallida. Intenta de nuevo.", Toast.LENGTH_SHORT).show()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Autenticación Biométrica")
            .setSubtitle("Usa tu huella digital para iniciar sesión")
            .setNegativeButtonText("Cancelar")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}