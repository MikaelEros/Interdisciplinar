package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import android.util.Base64
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import java.util.concurrent.Executor
import java.io.ByteArrayOutputStream

class GaleriaActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private val imagenesBitmaps = mutableListOf<Bitmap>()
    private lateinit var adapterBitmap: RecyclerView.Adapter<ImagenAdapter.ImagenViewHolder>
    
    // Fingerprint encryption system
    private lateinit var fingerprintEncryption: FingerprintEncryption
    private lateinit var chaoticEncryption: ChaoticEncryption
    private lateinit var userSpecificBiometric: UserSpecificBiometric
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private lateinit var executor: Executor
    
    private var pendingImageUri: Uri? = null

    private val REQUEST_CODE_PERMISSION = 100

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val imageUri: Uri? = result.data!!.data
            if (imageUri != null) {
                subirImagenConHuella(imageUri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            Log.d("GALERIA", "=== INICIANDO GALERIA ACTIVITY ===")
            FirebaseApp.initializeApp(this)
            setContentView(R.layout.activity_galeria)
            Log.d("GALERIA", "Layout cargado exitosamente")
            
            // Initialize fingerprint encryption system
            initializeFingerprintEncryption()
            
            recyclerView = findViewById(R.id.recyclerView)
            val btnSeleccionarFingerprint = findViewById<Button>(R.id.btnSeleccionarFingerprint)
            val btnRefresh = findViewById<Button>(R.id.btnRefresh)
            val btnCerrarSesion = findViewById<Button>(R.id.btnCerrarSesion)
            
            Log.d("GALERIA", "Elementos encontrados")

            // Inicializar adapter vacío
            adapterBitmap = object : RecyclerView.Adapter<ImagenAdapter.ImagenViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImagenAdapter.ImagenViewHolder {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_imagen, parent, false)
                    return ImagenAdapter.ImagenViewHolder(view)
                }
                override fun onBindViewHolder(holder: ImagenAdapter.ImagenViewHolder, position: Int) {
                    holder.imageView.setImageBitmap(imagenesBitmaps[position])
                    
                    // Configurar botón de descarga
                    holder.btnDescargar.setOnClickListener {
                        val bitmap = imagenesBitmaps[position]
                        descargarImagenAGaleria(bitmap, position)
                    }
                }
                override fun getItemCount() = imagenesBitmaps.size
            }
            
            Log.d("GALERIA", "Adapter creado")
            
            // Usar GridLayoutManager para mostrar imágenes en cuadrícula
            recyclerView.layoutManager = GridLayoutManager(this, 2)
            recyclerView.adapter = adapterBitmap
            
            Log.d("GALERIA", "RecyclerView configurado")

            // Cargar imágenes del usuario autenticado
            cargarImagenesUsuario(imagenesBitmaps, adapterBitmap)

            btnSeleccionarFingerprint.setOnClickListener {
                Log.d("GALERIA", "Botón seleccionar con huella presionado")
                if (tienePermisos()) {
                    abrirGaleria()
                } else {
                    pedirPermisos()
                }
            }
            
            btnRefresh.setOnClickListener {
                Log.d("GALERIA", "Botón refresh presionado")
                cargarImagenesUsuario(imagenesBitmaps, adapterBitmap)
                Toast.makeText(this, "Galería actualizada", Toast.LENGTH_SHORT).show()
            }
            
            btnCerrarSesion.setOnClickListener {
                Log.d("GALERIA", "Botón cerrar sesión presionado")
                cerrarSesion()
            }
            
            Log.d("GALERIA", "onCreate completado exitosamente")
            
        } catch (e: Exception) {
            Log.e("GALERIA", "Error en onCreate: ${e.localizedMessage}", e)
            Toast.makeText(this, "Error al inicializar la galería: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun tienePermisos(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun pedirPermisos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                REQUEST_CODE_PERMISSION
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_CODE_PERMISSION
            )
        }
    }

    private fun abrirGaleria() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        pickImageLauncher.launch(intent)
    }

    // Cargar imágenes del usuario autenticado
    private fun cargarImagenesUsuario(imagenesBitmaps: MutableList<Bitmap>, adapter: RecyclerView.Adapter<*>) {
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            
            // Limpiar lista antes de cargar nuevas imágenes
            imagenesBitmaps.clear()
            adapter.notifyDataSetChanged()
            
            Log.d("GALERIA", "Iniciando carga de imágenes para usuario: $uid")
            
            FirebaseFirestore.getInstance().collection("imagenes")
                .whereEqualTo("uid", uid)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    if (querySnapshot.isEmpty) {
                        Log.d("GALERIA", "No se encontraron imágenes para el usuario")
                        return@addOnSuccessListener
                    }
                    Log.d("GALERIA", "Encontradas ${querySnapshot.size()} imágenes para cargar")
                    for (doc in querySnapshot) {
                        val rutaStorage = doc.getString("rutaStorage") ?: continue
                        val ivBase64 = doc.getString("iv") ?: continue
                        val metodo = doc.getString("metodoEncriptacion") ?: "chaotic_system"
                        val iv = Base64.decode(ivBase64, Base64.DEFAULT)
                        // Descargar imagen encriptada
                        val storageRef = FirebaseStorage.getInstance().getReference(rutaStorage)
                        storageRef.getBytes(5 * 1024 * 1024).addOnSuccessListener { encryptedBytes ->
                            // Desencriptar con sistema caótico
                            val decryptedBitmap = decryptImageWithFingerprint(encryptedBytes)
                            if (decryptedBitmap != null) {
                                imagenesBitmaps.add(decryptedBitmap)
                                adapter.notifyItemInserted(imagenesBitmaps.size - 1)
                                Log.d("GALERIA", "Imagen cargada exitosamente. Total: ${imagenesBitmaps.size}")
                            } else {
                                Log.e("GALERIA", "Error al desencriptar imagen con sistema caótico")
                            }
                        }.addOnFailureListener { e ->
                            Log.e("GALERIA", "Error al descargar imagen: ${e.localizedMessage}", e)
                        }
                    }
                }.addOnFailureListener { e ->
                    Log.e("GALERIA", "Error al consultar Firestore: ${e.localizedMessage}", e)
                    Toast.makeText(this, "Error al cargar imágenes: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Log.e("GALERIA", "Error en cargarImagenesUsuario: ${e.localizedMessage}", e)
        }
    }

    // Process fingerprint encryption after biometric authentication
    private fun processFingerprintEncryption(uri: Uri) {
        try {
            Log.d("GALERIA", "Processing fingerprint encryption for URI: $uri")
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
                return
            }
            val uid = user.uid
            val contentResolver = this.contentResolver

            // Get file information
            var fileName = "imagen.jpg"
            var mimeType = "image/jpeg"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            mimeType = contentResolver.getType(uri) ?: "image/jpeg"

            // Read image bytes
            val inputStream = contentResolver.openInputStream(uri)
            val imageBytes = inputStream?.readBytes()
            inputStream?.close()
            if (imageBytes == null) {
                Toast.makeText(this, "No se pudo leer la imagen", Toast.LENGTH_SHORT).show()
                return
            }

            // Convert to bitmap for encryption
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            if (bitmap == null) {
                Toast.makeText(this, "No se pudo convertir la imagen", Toast.LENGTH_SHORT).show()
                return
            }

            // Encrypt with fingerprint using chaotic system
            val encryptedBytes = encryptImageWithFingerprint(bitmap)
            if (encryptedBytes == null) {
                Toast.makeText(this, "Error al encriptar la imagen con sistema caótico", Toast.LENGTH_SHORT).show()
                return
            }

            // Generate IV for compatibility
            val iv = ByteArray(16)
            java.security.SecureRandom().nextBytes(iv)

            // Upload to Firebase Storage
            val storageRef = FirebaseStorage.getInstance().reference.child("imagenes/$uid/$fileName.enc")
            val uploadTask = storageRef.putBytes(encryptedBytes)
            uploadTask.addOnSuccessListener { taskSnapshot ->
                Log.d("GALERIA", "Imagen encriptada con sistema caótico subida a Storage")
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    Log.d("GALERIA", "URL de descarga obtenida")
                    // Save metadata in Firestore
                    val metadata = hashMapOf(
                        "uid" to uid,
                        "nombreArchivo" to fileName,
                        "rutaStorage" to storageRef.path,
                        "fechaSubida" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                        "tamaño" to encryptedBytes.size,
                        "mimeType" to mimeType,
                        "encriptada" to true,
                        "iv" to Base64.encodeToString(iv, Base64.DEFAULT),
                        "metodoEncriptacion" to "chaotic_system"
                    )
                    FirebaseFirestore.getInstance().collection("imagenes")
                        .add(metadata)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Imagen encriptada y subida exitosamente", Toast.LENGTH_SHORT).show()
                            Log.d("GALERIA", "Metadata guardada en Firestore")
                            // Reload gallery after successful upload
                            cargarImagenesUsuario(imagenesBitmaps, adapterBitmap)
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error al guardar metadata: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            Log.e("GALERIA", "Error al guardar metadata: ${e.localizedMessage}", e)
                        }
                }.addOnFailureListener { e ->
                    Toast.makeText(this, "Error al obtener URL de imagen: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    Log.e("GALERIA", "Error al obtener URL: ${e.localizedMessage}", e)
                }
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Error al subir imagen: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                Log.e("GALERIA", "Error al subir imagen: ${e.localizedMessage}", e)
            }
        } catch (e: Exception) {
            Log.e("GALERIA", "Error en processFingerprintEncryption: ${e.localizedMessage}", e)
            Toast.makeText(this, "Error inesperado: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // Función para subir imagen con encriptación de huella digital
    private fun subirImagenConHuella(uri: Uri) {
        try {
            Log.d("GALERIA", "Iniciando subida de imagen con encriptación de huella")
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
                Log.e("GALERIA", "Usuario no autenticado")
                return
            }
            val uid = user.uid
            val contentResolver = this.contentResolver

            // Obtener nombre y tipo del archivo
            var fileName = "imagen.jpg"
            var mimeType = "image/jpeg"
            var fileSize: Long = 0
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                    fileSize = cursor.getLong(sizeIndex)
                }
            }
            mimeType = contentResolver.getType(uri) ?: "image/jpeg"
            Log.d("GALERIA", "Nombre archivo: $fileName, mimeType: $mimeType, size: $fileSize")

            // Leer bytes de la imagen
            val inputStream = contentResolver.openInputStream(uri)
            val imageBytes = inputStream?.readBytes()
            inputStream?.close()
            if (imageBytes == null) {
                Toast.makeText(this, "No se pudo leer la imagen", Toast.LENGTH_SHORT).show()
                Log.e("GALERIA", "No se pudo leer la imagen")
                return
            }
            Log.d("GALERIA", "Tamaño de la imagen en bytes: ${imageBytes.size}")

            // Convertir bytes a bitmap para encriptación con huella
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            if (bitmap == null) {
                Toast.makeText(this, "No se pudo convertir la imagen", Toast.LENGTH_SHORT).show()
                Log.e("GALERIA", "No se pudo convertir la imagen a bitmap")
                return
            }

            // Store the URI and trigger biometric authentication
            pendingImageUri = uri
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e("GALERIA", "Excepción inesperada en subida con huella: ${e.localizedMessage}", e)
            Toast.makeText(this, "Error inesperado en subida: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // Función para descargar imagen a la galería local
    private fun descargarImagenAGaleria(bitmap: Bitmap, position: Int) {
        try {
            Log.d("DESCARGA", "Iniciando descarga de imagen a galería local")
            
            // Verificar permisos de escritura
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Para Android 10+ usar MediaStore
                descargarConMediaStore(bitmap, position)
            } else {
                // Para versiones anteriores usar archivos directos
                descargarConArchivoDirecto(bitmap, position)
            }
            
        } catch (e: Exception) {
            Log.e("DESCARGA", "Error al descargar imagen: ${e.localizedMessage}", e)
            Toast.makeText(this, "Error al descargar imagen: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // Descarga usando MediaStore (Android 10+)
    private fun descargarConMediaStore(bitmap: Bitmap, position: Int) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "IMG_${timeStamp}_${position}.jpg"
            
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MiApp")
            }
            
            val contentResolver = contentResolver
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            
            uri?.let { imageUri ->
                contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    outputStream.flush()
                }
                
                Log.d("DESCARGA", "Imagen descargada exitosamente: $fileName")
                Toast.makeText(this, "Imagen descargada a galería: $fileName", Toast.LENGTH_LONG).show()
            } ?: run {
                Log.e("DESCARGA", "No se pudo crear URI para la imagen")
                Toast.makeText(this, "Error: No se pudo guardar la imagen", Toast.LENGTH_LONG).show()
            }
            
        } catch (e: Exception) {
            Log.e("DESCARGA", "Error en descarga MediaStore: ${e.localizedMessage}", e)
            Toast.makeText(this, "Error al descargar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // Descarga usando archivo directo (Android < 10)
    private fun descargarConArchivoDirecto(bitmap: Bitmap, position: Int) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "IMG_${timeStamp}_${position}.jpg"
            
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val appDir = File(picturesDir, "MiApp")
            if (!appDir.exists()) {
                appDir.mkdirs()
            }
            
            val imageFile = File(appDir, fileName)
            val outputStream = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.flush()
            outputStream.close()
            
            // Notificar a la galería que hay una nueva imagen
            val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            intent.data = Uri.fromFile(imageFile)
            sendBroadcast(intent)
            
            Log.d("DESCARGA", "Imagen descargada exitosamente: ${imageFile.absolutePath}")
            Toast.makeText(this, "Imagen descargada a galería: $fileName", Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            Log.e("DESCARGA", "Error en descarga archivo directo: ${e.localizedMessage}", e)
            Toast.makeText(this, "Error al descargar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            abrirGaleria()
        } else {
            Toast.makeText(this, "Permiso denegado", Toast.LENGTH_SHORT).show()
        }
    }

    // Función para cerrar sesión
    private fun cerrarSesion() {
        try {
            Log.d("GALERIA", "Cerrando sesión del usuario")
            
            // Cerrar sesión de Firebase Auth
            FirebaseAuth.getInstance().signOut()
            
            // Limpiar datos locales
            val prefs = getSharedPreferences("RecoveryGallery", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            
            // Cleanup fingerprint encryption
            fingerprintEncryption.cleanup()
            
            Toast.makeText(this, "Sesión cerrada exitosamente", Toast.LENGTH_SHORT).show()
            
            // Volver a MainActivity
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            
        } catch (e: Exception) {
            Log.e("GALERIA", "Error al cerrar sesión: ${e.localizedMessage}", e)
            Toast.makeText(this, "Error al cerrar sesión: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
    
    // Initialize fingerprint encryption system
    private fun initializeFingerprintEncryption() {
        try {
            Log.d("GALERIA", "Inicializando sistema de encriptación de huella")
            
            fingerprintEncryption = FingerprintEncryption(this)
            Log.d("GALERIA", "FingerprintEncryption creado")
            
            chaoticEncryption = ChaoticEncryption()
            Log.d("GALERIA", "ChaoticEncryption creado")
            
            userSpecificBiometric = UserSpecificBiometric(this)
            Log.d("GALERIA", "UserSpecificBiometric creado")
            
            val success = fingerprintEncryption.initialize()
            Log.d("GALERIA", "Inicialización de FingerprintEncryption: $success")
            
            if (success) {
                Log.d("GALERIA", "Fingerprint encryption system initialized successfully")
                setupBiometricPrompt()
            } else {
                Log.e("GALERIA", "Failed to initialize fingerprint encryption system")
                Toast.makeText(this, "Error initializing encryption system", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("GALERIA", "Error initializing fingerprint encryption: ${e.message}", e)
            Toast.makeText(this, "Error initializing encryption: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    // Setup biometric prompt for fingerprint authentication
    private fun setupBiometricPrompt() {
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.d("GALERIA", "Biometric authentication succeeded")
                try {
                    // Process pending image upload if exists
                    pendingImageUri?.let { uri ->
                        try {
                            processFingerprintEncryption(uri)
                        } catch (e: Exception) {
                            Log.e("GALERIA", "Error en processFingerprintEncryption: ${e.localizedMessage}", e)
                            Toast.makeText(this@GaleriaActivity, "Error al encriptar imagen: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                        pendingImageUri = null
                    }
                } catch (e: Exception) {
                    Log.e("GALERIA", "Excepción inesperada tras autenticación biométrica: ${e.localizedMessage}", e)
                    Toast.makeText(this@GaleriaActivity, "Error inesperado tras biometría: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.e("GALERIA", "Biometric authentication error: $errString")
                Toast.makeText(this@GaleriaActivity, "Error: $errString", Toast.LENGTH_SHORT).show()
                pendingImageUri = null
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.e("GALERIA", "Biometric authentication failed")
                Toast.makeText(this@GaleriaActivity, "Autenticación fallida", Toast.LENGTH_SHORT).show()
                pendingImageUri = null
            }
        })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verificación biométrica")
            .setSubtitle("Usa tu huella digital para encriptar/desencriptar")
            .setNegativeButtonText("Cancelar")
            .build()
    }
    
    // Encriptar imagen con sistema caótico usando 360 bits del usuario
    private fun encryptImageWithFingerprint(bitmap: Bitmap): ByteArray? {
        return try {
            Log.d("GALERIA", "Iniciando encriptación con sistema caótico")
            // Escalar la imagen antes de encriptar
            val scaledBitmap = scaleBitmapForEncryption(bitmap)
            // Definir userId y userEmail para el flujo actual
            val currentUser = FirebaseAuth.getInstance().currentUser
            val userId = currentUser?.uid ?: "default"
            val userEmail = currentUser?.email ?: ""
            // Generar 360 bits únicos para el usuario usando clave específica
            Log.d("GALERIA", "Generando 360 bits para usuario: $userId")
            val user360Bits = fingerprintEncryption.generateUserSpecific360Bits(userId)
            Log.d("GALERIA", "360 bits generados para el usuario: ${user360Bits.take(50)}...")
            // Encriptar usando el sistema caótico
            Log.d("GALERIA", "Iniciando encriptación caótica con bitmap: ${scaledBitmap.width}x${scaledBitmap.height}")
            val encryptedBytes = chaoticEncryption.encryptImage(scaledBitmap, user360Bits)
            if (encryptedBytes != null) {
                Log.d("GALERIA", "Encriptación caótica exitosa. Tamaño: ${encryptedBytes.size} bytes")
                encryptedBytes
            } else {
                Log.e("GALERIA", "Error en encriptación caótica: resultado null")
                null
            }
        } catch (e: Exception) {
            Log.e("GALERIA", "Error en encriptación con sistema caótico: ${e.localizedMessage}", e)
            null
        }
    }

    // Desencriptar imagen con sistema caótico usando 360 bits del usuario
    private fun decryptImageWithFingerprint(encryptedBytes: ByteArray): Bitmap? {
        return try {
            Log.d("GALERIA", "Iniciando desencriptación con sistema caótico")
            Log.d("GALERIA", "Tamaño de datos encriptados: ${encryptedBytes.size} bytes")
            // Definir userId y userEmail para el flujo actual
            val currentUser = FirebaseAuth.getInstance().currentUser
            val userId = currentUser?.uid ?: "default"
            val userEmail = currentUser?.email ?: ""
            
            // Generar 360 bits únicos para el usuario (mismos que en encriptación)
            Log.d("GALERIA", "Generando 360 bits para desencriptación del usuario: $userId")
            val user360Bits = fingerprintEncryption.generateUserSpecific360Bits(userId)
            Log.d("GALERIA", "360 bits generados para desencriptación: ${user360Bits.take(50)}...")
            
            // Desencriptar usando el sistema caótico
            val bitmap = chaoticEncryption.decryptImage(encryptedBytes, user360Bits)
            if (bitmap != null) {
                Log.d("GALERIA", "Desencriptación caótica exitosa")
                bitmap
            } else {
                Log.e("GALERIA", "Error en desencriptación caótica: resultado null")
                null
            }
        } catch (e: Exception) {
            Log.e("GALERIA", "Error en desencriptación con sistema caótico: ${e.localizedMessage}", e)
            null
        }
    }

    // Escalar imagen antes de encriptar para evitar OOM
    private fun scaleBitmapForEncryption(bitmap: Bitmap, maxSize: Int = 1024): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxSize && height <= maxSize) return bitmap
        val aspectRatio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (aspectRatio > 1) {
            newWidth = maxSize
            newHeight = (maxSize / aspectRatio).toInt()
        } else {
            newHeight = maxSize
            newWidth = (maxSize * aspectRatio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
