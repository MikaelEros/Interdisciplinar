package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.FirebaseApp
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class GaleriaActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private val imagenesSeleccionadas = mutableListOf<Uri>()
    private lateinit var adapter: ImagenAdapter

    private val REQUEST_CODE_PERMISSION = 100

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val imageUri: Uri? = result.data!!.data
            if (imageUri != null) {
                imagenesSeleccionadas.add(imageUri)
                adapter.notifyItemInserted(imagenesSeleccionadas.size - 1)
                subirImagenAFirebase(imageUri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContentView(R.layout.activity_galeria)

        recyclerView = findViewById(R.id.recyclerView)
        val btnSeleccionar = findViewById<Button>(R.id.btnSeleccionar)

        adapter = ImagenAdapter(imagenesSeleccionadas)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        btnSeleccionar.setOnClickListener {
            if (tienePermisos()) {
                abrirGaleria()
            } else {
                pedirPermisos()
            }
        }
    }
//Permiso para poder accer a la galeria
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
    private fun subirImagenAFirebase(uri: Uri) {
        val storage = FirebaseStorage.getInstance()
        val nombreArchivo = "imagenes/${UUID.randomUUID()}.jpg"
        val referencia = storage.reference.child(nombreArchivo)

        referencia.putFile(uri)
            .addOnSuccessListener {
                Toast.makeText(this, "Imagen subida a Firebase Storage", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al subir: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

}
