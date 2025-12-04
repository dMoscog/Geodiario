package com.example.geodiario

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- MODELO DE DATOS ---
data class BitacoraEntry(
    val id: Long = System.currentTimeMillis(),
    val latitud: Double,
    val longitud: Double,
    val altitud: Double,
    val precision: Float,
    val nota: String,
    val fechaHora: String,
    val fotoPath: String? = null
)


val NeonCyan = Color(0xFF00E5FF)
val NeonPurple = Color(0xFFD500F9)
val DeepDark = Color(0xFF0F172A)
val SurfaceDark = Color(0xFF1E293B)
val GlassWhite = Color.White.copy(alpha = 0.1f)

class MainActivity : ComponentActivity() {
    // CORRECCIÓN 1: Aseguramos el 'lateinit' aquí
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            // TEMA OSCURO FUTURISTA
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = NeonCyan,
                    secondary = NeonPurple,
                    background = DeepDark,
                    surface = SurfaceDark,
                    onSurface = Color.White
                )
            ) {
                // Fondo con gradiente global
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF020024), Color(0xFF090979), Color(0xFF00151C))
                            )
                        )
                ) {
                    MainApp(fusedLocationClient)
                }
            }
        }
    }
}

// --- UTILIDADES ---
fun crearArchivoImagen(context: Context): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
}

fun guardarEnArchivo(context: Context, lista: List<BitacoraEntry>) {
    try {
        val jsonArray = JSONArray()
        lista.forEach { entry ->
            val obj = JSONObject().apply {
                put("id", entry.id)
                put("lat", entry.latitud)
                put("lon", entry.longitud)
                put("alt", entry.altitud)
                put("prec", entry.precision.toDouble())
                put("nota", entry.nota)
                put("time", entry.fechaHora)
                put("path", entry.fotoPath ?: "")
            }
            jsonArray.put(obj)
        }
        File(context.filesDir, "geo_datos.json").writeText(jsonArray.toString())
    } catch (e: Exception) { e.printStackTrace() }
}

fun leerDeArchivo(context: Context): List<BitacoraEntry> {
    val lista = mutableListOf<BitacoraEntry>()
    val file = File(context.filesDir, "geo_datos.json")
    if (file.exists()) {
        try {
            val jsonArray = JSONArray(file.readText())
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                lista.add(BitacoraEntry(
                    id = obj.getLong("id"),
                    latitud = obj.getDouble("lat"),
                    longitud = obj.getDouble("lon"),
                    altitud = obj.getDouble("alt"),
                    precision = obj.getDouble("prec").toFloat(),
                    nota = obj.getString("nota"),
                    fechaHora = obj.getString("time"),
                    fotoPath = if (obj.has("path")) obj.getString("path").takeIf { it.isNotEmpty() } else null
                ))
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
    return lista
}

// --- NAVEGACIÓN ---
@Composable
fun MainApp(fusedLocationClient: FusedLocationProviderClient) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val historial = remember { mutableStateListOf<BitacoraEntry>() }

    LaunchedEffect(Unit) {
        historial.addAll(leerDeArchivo(context))
    }

    Scaffold(
        containerColor = Color.Transparent, // Para ver el fondo global
        bottomBar = { NeonBottomBar(navController) }
    ) { paddingValues ->
        NavHost(navController, startDestination = "home", Modifier.padding(paddingValues)) {
            composable("home") {
                HomeScreen(fusedLocationClient, historial) { guardarEnArchivo(context, historial) }
            }
            composable("list") {
                HistoryScreen(historial) { guardarEnArchivo(context, historial) }
            }
        }
    }
}

@Composable
fun NeonBottomBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Barra flotante estilo cristal
    Box(modifier = Modifier.padding(16.dp)) {
        NavigationBar(
            containerColor = Color.Black.copy(alpha = 0.6f),
            contentColor = NeonCyan,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, Brush.horizontalGradient(listOf(NeonCyan, NeonPurple)), RoundedCornerShape(24.dp))
        ) {
            NavigationBarItem(
                icon = { Icon(Icons.Rounded.CameraAlt, null) },
                label = { Text("CAPTURA", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                selected = currentRoute == "home",
                onClick = { navController.navigate("home") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Black,
                    selectedTextColor = NeonCyan,
                    indicatorColor = NeonCyan
                )
            )
            NavigationBarItem(
                icon = { Icon(Icons.Rounded.PhotoLibrary, null) },
                label = { Text("DATOS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                selected = currentRoute == "list",
                onClick = { navController.navigate("list") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Black,
                    selectedTextColor = NeonPurple,
                    indicatorColor = NeonPurple
                )
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun HomeScreen(fusedLocationClient: FusedLocationProviderClient, historial: MutableList<BitacoraEntry>, onSave: () -> Unit) {
    val context = LocalContext.current
    var ubicacion by remember { mutableStateOf<Location?>(null) }
    var mensaje by remember { mutableStateOf("SISTEMA LISTO") }
    var nota by remember { mutableStateOf("") }

    // Foto
    var fotoUri by remember { mutableStateOf<Uri?>(null) }
    var fotoFile by remember { mutableStateOf<File?>(null) }

    // Animación de pulso
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "scale"
    )

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (!success) { fotoUri = null; fotoFile = null }
    }

    fun tomarFoto() {
        val file = crearArchivoImagen(context)
        fotoFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        fotoUri = uri
        cameraLauncher.launch(uri)
    }

    fun getLocation() {
        mensaje = "ESCANEANDO SATÉLITES..."
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    ubicacion = loc
                    mensaje = "OBJETIVO FIJADO"
                } else mensaje = "SEÑAL PERDIDA"
            }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.ACCESS_FINE_LOCATION] == true) getLocation()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Encabezado Tech - ACTUALIZADO AQUÍ
        Text(
            text = "GeoDiario", // Cambiado de GEO-TRACKER V1
            color = NeonCyan,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
            color = Color.Gray,
            fontFamily = FontFamily.Monospace
        )

        Spacer(Modifier.height(30.dp))

        // PORTAL DE CÁMARA (EL MONSTRUO)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(280.dp)
                .scale(if (fotoUri == null) scale else 1f) // Solo pulsa si no hay foto
                .shadow(20.dp, CircleShape, spotColor = NeonCyan)
                .clip(CircleShape)
                .background(Color.Black)
                .border(4.dp, Brush.sweepGradient(listOf(NeonCyan, NeonPurple, NeonCyan)), CircleShape)
                .clickable { tomarFoto() }
        ) {
            if (fotoUri != null) {
                AsyncImage(
                    model = fotoUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Overlay de edición
                Box(Modifier.fillMaxSize().background(Color.Black.copy(0.3f)), Alignment.Center) {
                    Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(48.dp))
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Camera, null, tint = NeonCyan, modifier = Modifier.size(64.dp))
                    Text("INICIAR ESCANEO", color = NeonCyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(30.dp))

        // DASHBOARD DE DATOS ESTILO HUD
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GlassWhite)
                .border(1.dp, NeonPurple.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(16.dp),
            Arrangement.SpaceBetween
        ) {
            Column {
                Text("ESTADO", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text(mensaje, color = if(ubicacion!=null) NeonCyan else Color.Red, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            if (ubicacion != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("COORDINADAS", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("${String.format("%.4f", ubicacion!!.latitude)} / ${String.format("%.4f", ubicacion!!.longitude)}", color = Color.White, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // INPUT NEÓN - ACTUALIZADO AQUÍ
        OutlinedTextField(
            value = nota,
            onValueChange = { nota = it },
            label = { Text("NOTA", color = NeonCyan) }, // Cambiado de NOTAS DE MISIÓN
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.LightGray,
                cursorColor = NeonCyan
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(20.dp))

        // BOTÓN GUARDAR GLITCH
        Button(
            onClick = {
                if (ubicacion != null && nota.isNotEmpty()) {
                    val entry = BitacoraEntry(
                        latitud = ubicacion!!.latitude,
                        longitud = ubicacion!!.longitude,
                        altitud = ubicacion!!.altitude,
                        precision = ubicacion!!.accuracy,
                        nota = nota,
                        fechaHora = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date()),
                        fotoPath = fotoFile?.absolutePath
                    )
                    historial.add(0, entry)
                    onSave()
                    nota = ""
                    fotoUri = null; fotoFile = null
                    Toast.makeText(context, "REGISTRO SUBIDO A LA RED", Toast.LENGTH_SHORT).show()
                } else {
                    if (ubicacion == null) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            getLocation()
                        } else permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                    } else {
                        Toast.makeText(context, "ERROR: CAMPO VACÍO", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp).shadow(10.dp, spotColor = NeonPurple),
            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("GUARDAR REGISTRO", fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        }
    }
}

// --- PANTALLA 2: DATOS DE CAMPO ---
// CORRECCIÓN 3: Agregamos @OptIn aquí también
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(historial: MutableList<BitacoraEntry>, onSave: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("BASE DE DATOS", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            IconButton(onClick = { historial.clear(); onSave() }) {
                Icon(Icons.Default.DeleteForever, null, tint = Color.Red)
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(historial) { item -> NeonHistoryCard(item) }
        }
    }
}

@Composable
fun NeonHistoryCard(item: BitacoraEntry) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(0.5f)),
        border = BorderStroke(1.dp, NeonCyan.copy(0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            if (item.fotoPath != null) {
                Box {
                    AsyncImage(
                        model = File(item.fotoPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(150.dp)
                    )
                    // Gradiente sobre la imagen
                    Box(Modifier.fillMaxWidth().height(150.dp).background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
                    ))
                    // Badge de fecha
                    Box(Modifier.padding(8.dp).align(Alignment.BottomEnd).background(Color.Black, RoundedCornerShape(4.dp)).padding(4.dp)) {
                        Text(item.fechaHora, color = NeonCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Column(Modifier.padding(16.dp)) {
                Text(item.nota.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(8.dp))

                // Datos técnicos
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    TechData("LAT", String.format("%.4f", item.latitud))
                    TechData("LON", String.format("%.4f", item.longitud))
                    TechData("ALT", "${item.altitud.toInt()}m")
                }
            }
        }
    }
}

@Composable
fun TechData(label: String, value: String) {
    Column {
        Text(label, color = Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = NeonPurple, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}