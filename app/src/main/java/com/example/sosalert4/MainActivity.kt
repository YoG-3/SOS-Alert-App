package com.example.sosalert4

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class EmergencyContact(val name: String, val phone: String, val email: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SOSApp()
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SOSApp() {
    val context = LocalContext.current

    val gradients = listOf(
        listOf(Color(0xFFB91313), Color(0xFFF23557)),
        listOf(Color(0xFF1FA2FF), Color(0xFF12D8FA)),
        listOf(Color(0xFFF7971E), Color(0xFFFFD200)),
        listOf(Color(0xFF2B5876), Color(0xFF4E4376))
    )
    val gradientNames = listOf("Red Alert", "Blue Breeze", "Amber Safe", "Twilight")

    var selectedGradientIdx by remember { mutableStateOf(0) }
    var isAlarmEnabled by remember { mutableStateOf(true) }
    var isMenuOpen by remember { mutableStateOf(false) }
    var isAboutUsOpen by remember { mutableStateOf(false) }
    var isInstructionsOpen by remember { mutableStateOf(false) }

    var contacts by remember { mutableStateOf(listOf<EmergencyContact>()) }
    var newContactName by remember { mutableStateOf("") }
    var newContactPhone by remember { mutableStateOf("") }
    var newContactEmail by remember { mutableStateOf("") }
    var showAddContactDialog by remember { mutableStateOf(false) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    val scope = rememberCoroutineScope()

    suspend fun getCurrentLocation(): Location? = suspendCoroutine { cont ->
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            val gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            cont.resume(gps ?: network)
        } else {
            cont.resume(null)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = gradients[selectedGradientIdx]))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isAlarmEnabled) "Alarm: ON" else "Alarm: OFF",
                    color = Color.White,
                    fontSize = 16.sp
                )
                Switch(
                    checked = isAlarmEnabled,
                    onCheckedChange = { isAlarmEnabled = it },
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(Modifier.height(40.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(220.dp)
                    .shadow(18.dp, CircleShape)
                    .background(Color.DarkGray, CircleShape)
            ) {
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                            != PackageManager.PERMISSION_GRANTED) {
                            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                            return@Button
                        }
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            locationPermissionLauncher.launch(
                              arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                            return@Button
                        }

                        if (isAlarmEnabled) {
                            val mediaPlayer = MediaPlayer.create(context, R.raw.alarm)
                            mediaPlayer.start()
                            mediaPlayer.setOnCompletionListener { mp -> mp.release() }
                        }

                        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(1500, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(1500)
                        }

                        scope.launch {
                            val location = getCurrentLocation()
                            val locationMsg = if (location != null) {
                                "My location: https://maps.google.com/?q=${location.latitude},${location.longitude}"
                            } else {
                                "Location unavailable."
                            }
                            val baseMsg = "SOS! I am in emergency. $locationMsg"

                            if (contacts.isEmpty()) {
                                Toast.makeText(context, "No emergency contacts added.", Toast.LENGTH_SHORT).show()
                                return@launch
                            }

                            repeat(3) { repeatIdx ->
                                contacts.forEach { contact ->
                                    try {
                                        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("smsto:${contact.phone}")
                                            putExtra("sms_body", baseMsg)
                                        }
                                        context.startActivity(smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        Toast.makeText(context, "SMS sent to ${contact.name}", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Failed to send SMS to ${contact.name}", Toast.LENGTH_SHORT).show()
                                    }

                                    try {
                                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("mailto:${contact.email}")
                                            putExtra(Intent.EXTRA_SUBJECT, "EMERGENCY! SOS!")
                                            putExtra(Intent.EXTRA_TEXT, baseMsg)
                                        }
                                        context.startActivity(Intent.createChooser(emailIntent, "Send Email")
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Failed to send Email to ${contact.name}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                if (repeatIdx < 2) delay(20000L)
                            }
                        }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.size(180.dp)
                ) {
                    Text("SOS", fontWeight = FontWeight.Bold, fontSize = 56.sp, color = Color.White)
                }
            }

            Spacer(Modifier.height(38.dp))

            Text(
                text = "PRESS THE BUTTON IN CASE OF EMERGENCY",
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 18.dp)
            )
        }

        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 38.dp)
        ) {
            Button(
                onClick = { isMenuOpen = true },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .height(54.dp)
            ) {
                Text("MENU", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        if (isMenuOpen) {
            AlertDialog(
                onDismissRequest = { isMenuOpen = false },
                confirmButton = {},
                dismissButton = {},
                title = { Text("Menu") },
                text = {
                    Column {
                        Text("Select Background Theme", fontWeight = FontWeight.SemiBold)
                        gradientNames.forEachIndexed { i, name ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = selectedGradientIdx == i,
                                    onClick = { selectedGradientIdx = i }
                                )
                                Text(name)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Emergency Contacts", fontWeight = FontWeight.SemiBold)
                        LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
                            items(contacts) { contact ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${contact.name}: ${contact.phone}")
                                    Text("❌", modifier = Modifier.clickable {
                                        contacts = contacts.filter { it != contact }
                                    })
                                }
                            }
                        }
                        Button(onClick = { showAddContactDialog = true }, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Add Contact")
                        }
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Button(onClick = {
                            isAboutUsOpen = true
                            isMenuOpen = false
                        }) { Text("About Us") }
                        Button(onClick = {
                            isInstructionsOpen = true
                            isMenuOpen = false
                        }) { Text("Instructions") }
                    }
                }
            )
        }

        if (showAddContactDialog) {
            AlertDialog(
                onDismissRequest = { showAddContactDialog = false },
                confirmButton = {
                    Button(onClick = {
                        if (newContactName.isNotBlank() && newContactPhone.isNotBlank() && newContactEmail.isNotBlank()) {
                            contacts = contacts + EmergencyContact(newContactName, newContactPhone, newContactEmail)
                            newContactName = ""
                            newContactPhone = ""
                            newContactEmail = ""
                            showAddContactDialog = false
                        }
                    }) { Text("Save") }
                },
                dismissButton = {
                    Button(onClick = { showAddContactDialog = false }) { Text("Cancel") }
                },
                title = { Text("Add Contact") },
                text = {
                    Column {
                        OutlinedTextField(value = newContactName, onValueChange = { newContactName = it }, label = { Text("Name") })
                        OutlinedTextField(value = newContactPhone, onValueChange = { newContactPhone = it }, label = { Text("Phone") })
                        OutlinedTextField(value = newContactEmail, onValueChange = { newContactEmail = it }, label = { Text("Email") })
                    }
                }
            )
        }

        if (isAboutUsOpen) {
            AlertDialog(
                onDismissRequest = { isAboutUsOpen = false },
                confirmButton = {
                    Button(onClick = { isAboutUsOpen = false }) { Text("Close") }
                },
                title = { Text("About Us") },
                text = { Text("This app is developed to help users in emergencies. Press the SOS button to send your location and alert to your emergency contacts.") }
            )
        }

        if (isInstructionsOpen) {
            AlertDialog(
                onDismissRequest = { isInstructionsOpen = false },
                confirmButton = {
                    Button(onClick = { isInstructionsOpen = false }) { Text("Close") }
                },
                title = { Text("Instructions") },
                text = {
                    Text(
                        "1. Add at least one emergency contact.\n" +
                                "2. Make sure location and SMS permissions are granted.\n" +
                                "3. In emergency, press SOS.\n" +
                                "4. Your location will be shared via SMS and Email.\n" +
                                "5. SOS is sent 3 times every 20 seconds."
                    )
                }
            )
        }
    }
}
