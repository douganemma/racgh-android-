package com.racghana.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

private val Navy = Color(0xFF10243A)
private val Green = Color(0xFF0D8A68)
private val Canvas = Color(0xFFF5F7F8)

data class CarItem(
    val id: String,
    val name: String,
    val type: String,
    val seats: String,
    val transmission: String,
    val price: String,
    val image: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RaCApp() }
    }
}

@Composable
fun RaCApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("racghana_session", 0) }
    var token by remember { mutableStateOf(prefs.getString("token", null)) }
    var tab by remember { mutableStateOf("Home") }
    var selected by remember { mutableStateOf<CarItem?>(null) }
    var cars by remember { mutableStateOf<List<CarItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Connecting to RaC Ghana…") }
    var configLoaded by remember { mutableStateOf(false) }
    var gateways by remember { mutableStateOf(0) }

    suspend fun loadInitial() {
        loading = true
        try {
            // Load the website settings and payment methods alongside the homepage and live car search.
            val responses = kotlinx.coroutines.coroutineScope {
                listOf(
                    async { RacApi.get("configs") },
                    async { RacApi.get("gateways") },
                    async { RacApi.get("home-page") },
                    async { RacApi.get("car/search") }
                ).map { it.await() }
            }
            configLoaded = responses[0].status in 200..299
            gateways = RacApi.rows(responses[1]).size
            val liveCars = RacApi.rows(responses[3]).mapNotNull { parseCar(it) }
            cars = liveCars
            message = if (liveCars.isEmpty()) {
                "No vehicles were returned by the car search service (HTTP ${responses[3].status}). Pull to refresh or try again."
            } else {
                "${liveCars.size} vehicles loaded from RaC Ghana."
            }
        } catch (e: Exception) {
            message = "Could not connect: ${e.localizedMessage ?: "Please try again."}"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadInitial() }

    MaterialTheme(colorScheme = lightColorScheme(primary = Green, secondary = Navy, background = Canvas)) {
        Scaffold(
            containerColor = Canvas,
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    listOf("Home", "Explore", "Bookings", "Profile").forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item; selected = null },
                            icon = {
                                Icon(when (item) {
                                    "Home" -> Icons.Default.Home
                                    "Explore" -> Icons.Default.Search
                                    "Bookings" -> Icons.Default.ConfirmationNumber
                                    else -> Icons.Default.Person
                                }, null)
                            },
                            label = { Text(item) }
                        )
                    }
                }
            }
        ) { padding ->
            when {
                selected != null -> CarDetailsScreen(
                    car = selected!!,
                    onBack = { selected = null },
                    onRefresh = {
                        scope.launch {
                            val id = selected!!.id
                            val detail = runCatching { RacApi.get("car/detail/$id") }.getOrNull()
                            val availability = runCatching { RacApi.get("car/availability/$id") }.getOrNull()
                            message = when {
                                detail == null -> "Vehicle detail request failed."
                                detail.status !in 200..299 -> "Vehicle detail unavailable (HTTP ${detail.status})."
                                availability == null -> "Details loaded; availability could not be checked."
                                availability.status in 200..299 -> "Vehicle details and availability checked with RaC."
                                else -> "Details loaded; availability returned HTTP ${availability.status}."
                            }
                        }
                    },
                    modifier = Modifier.padding(padding)
                )
                tab == "Home" -> HomeScreen(
                    cars = cars,
                    loading = loading,
                    message = message,
                    onRefresh = { scope.launch { loadInitial() } },
                    onCar = { selected = it },
                    onExplore = { tab = "Explore" },
                    modifier = Modifier.padding(padding)
                )
                tab == "Explore" -> ExploreScreen(
                    cars = cars,
                    loading = loading,
                    onRefresh = { scope.launch { loadInitial() } },
                    onCar = { selected = it },
                    modifier = Modifier.padding(padding)
                )
                tab == "Bookings" -> BookingScreen(token, modifier = Modifier.padding(padding))
                else -> ProfileScreen(
                    token = token,
                    onLogin = { email, password ->
                        scope.launch {
                            loading = true
                            try {
                                val result = RacApi.post("auth/login", mapOf("email" to email, "password" to password))
                                val body = RacApi.unwrap(result)
                                val newToken = RacApi.firstText(body, "token", "access_token")
                                if (result.status in 200..299 && newToken.isNotBlank()) {
                                    token = newToken
                                    prefs.edit().putString("token", newToken).apply()
                                } else {
                                    message = RacApi.firstText(result.json(), "message", "error").ifBlank {
                                        "Login failed (HTTP ${result.status}). Check your details."
                                    }
                                }
                            } catch (e: Exception) {
                                message = "Login error: ${e.localizedMessage}"
                            } finally { loading = false }
                        }
                    },
                    onLogout = {
                        scope.launch {
                            runCatching { RacApi.post("auth/logout", emptyMap(), token) }
                            token = null
                            prefs.edit().remove("token").apply()
                        }
                    },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

private fun parseCar(obj: JSONObject): CarItem? {
    val id = RacApi.firstText(obj, "id", "slug", "objectID")
    val name = RacApi.firstText(obj, "title", "name", "car_name", "vehicle_name")
    if (name.isBlank()) return null
    val priceValue = RacApi.firstText(obj, "price", "sale_price", "daily_rate", "regular_price", "price_per_day")
    val currency = RacApi.firstText(obj, "currency", "currency_code").ifBlank { "GH₵" }
    val price = if (priceValue.isBlank()) "Price on request" else if (priceValue.contains("GH₵") || priceValue.contains("GHS")) priceValue else "$currency $priceValue / day"
    val location = RacApi.firstText(obj, "location_name", "location", "city")
    val category = RacApi.firstText(obj, "category_name", "type", "service_type").ifBlank { "Car rental" }
    return CarItem(
        id = id,
        name = name,
        type = listOf(category, location).filter { it.isNotBlank() }.joinToString(" · "),
        seats = RacApi.firstText(obj, "seats", "number_of_seats", "passengers").ifBlank { "Seats vary" },
        transmission = RacApi.firstText(obj, "transmission", "gear").ifBlank { "Ask provider" },
        price = price,
        image = RacApi.firstText(obj, "image", "thumbnail", "image_url", "banner")
    )
}

@Composable
fun HomeScreen(
    cars: List<CarItem>,
    loading: Boolean,
    message: String,
    onRefresh: () -> Unit,
    onCar: (CarItem) -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(Navy).padding(horizontal = 22.dp, vertical = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RaC", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(" GHANA", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF71D9B5))
                    Spacer(Modifier.weight(1f))
                    Text("Car rental made easy", color = Color.White.copy(.8f), fontSize = 12.sp)
                }
                Spacer(Modifier.height(30.dp))
                Text("Go anywhere\nwith RaC.", fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text("Find the right ride for every journey.", color = Color.White.copy(.8f), fontSize = 15.sp)
                Spacer(Modifier.height(22.dp))
                SearchPanel(onExplore)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Available hire cars", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Navy)
                    Text(if (loading) "Loading live listings…" else message, fontSize = 12.sp, color = Color.Gray)
                }
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
        }
        if (cars.isEmpty() && !loading) item {
            EmptyState("No live vehicles found", "The app is connected to the RaC API, but the search endpoint returned no vehicle records.", onRefresh)
        }
        items(cars) { car -> CarCard(car) { onCar(car) } }
    }
}

@Composable
fun SearchPanel(onSearch: () -> Unit) {
    var location by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp)) {
        Text("Find your rental", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Navy)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text("Pick-up location") }, singleLine = true, leadingIcon = { Icon(Icons.Default.LocationOn, null) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(from, { from = it }, Modifier.weight(1f), label = { Text("Pick-up date") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true)
            OutlinedTextField(to, { to = it }, Modifier.weight(1f), label = { Text("Return date") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true)
        }
        Button(onClick = onSearch, Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Green), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Search cars", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CarCard(car: CarItem, onClick: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 7.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(135.dp).background(Color(0xFFE7ECEF)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.DirectionsCar, null, tint = Color(0xFF9AAAB4), modifier = Modifier.size(65.dp))
            Surface(Modifier.align(Alignment.TopStart).padding(12.dp), color = Green, shape = RoundedCornerShape(6.dp)) {
                Text(" LIVE LISTING ", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.padding(15.dp)) {
            Text(car.type.uppercase(), fontSize = 10.sp, color = Green, fontWeight = FontWeight.Bold)
            Text(car.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Navy)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Label(Icons.Default.People, car.seats); Label(Icons.Default.Settings, car.transmission)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(car.price, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Green)
                Spacer(Modifier.weight(1f)); Text("View details  →", color = Navy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun Label(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ExploreScreen(cars: List<CarItem>, loading: Boolean, onRefresh: () -> Unit, onCar: (CarItem) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Explore cars", Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Navy)
            TextButton(onClick = onRefresh) { Text(if (loading) "Loading…" else "Refresh") }
        }
        if (cars.isEmpty() && !loading) EmptyState("No vehicles to show", "Try refreshing the live car catalogue.", onRefresh)
        LazyColumn { items(cars) { CarCard(it) { onCar(it) } } }
    }
}

@Composable
fun CarDetailsScreen(car: CarItem, onBack: () -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    var detailMessage by remember { mutableStateOf("Vehicle details are loaded from the live search result. Tap below to check the full detail and availability endpoints.") }
    Column(modifier.fillMaxSize().background(Canvas)) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
            Text("Vehicle details", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Navy)
        }
        Column(Modifier.weight(1f).padding(20.dp)) {
            Box(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFE7ECEF)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.DirectionsCar, null, tint = Color.Gray, modifier = Modifier.size(90.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text(car.name, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Navy)
            Text(car.type, color = Green, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(15.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) { Label(Icons.Default.People, car.seats); Label(Icons.Default.Settings, car.transmission) }
            Spacer(Modifier.height(22.dp))
            Text("Rental details", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Navy)
            Text("Daily rate: ${car.price}. Confirm final price, terms and vehicle availability with the provider.", Modifier.padding(top = 8.dp), color = Color.DarkGray)
            Text(detailMessage, Modifier.padding(top = 14.dp), color = Color.Gray, fontSize = 13.sp)
        }
        Button(onClick = onRefresh, Modifier.fillMaxWidth().padding(20.dp).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Green)) {
            Text("Check live details & availability")
        }
    }
}

@Composable
fun BookingScreen(token: String?, modifier: Modifier = Modifier) {
    var bookings by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var status by remember { mutableStateOf(if (token.isNullOrBlank()) "Sign in to see your booking history." else "Loading your booking history…") }
    LaunchedEffect(token) {
        if (!token.isNullOrBlank()) {
            try {
                val result = RacApi.get("user/booking-history", bearerToken = token)
                bookings = RacApi.rows(result)
                status = if (result.status in 200..299) "Your booking history" else "Booking history request failed (HTTP ${result.status})."
            } catch (e: Exception) { status = "Could not load bookings: ${e.localizedMessage}" }
        }
    }
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("Your bookings", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text(status, Modifier.padding(top = 8.dp, bottom = 14.dp), color = Color.Gray)
        bookings.forEach { booking ->
            Card(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(RacApi.firstText(booking, "code", "booking_code", "id").ifBlank { "Booking" }, fontWeight = FontWeight.Bold)
                    Text(RacApi.firstText(booking, "status", "booking_status", "created_at"), color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(token: String?, onLogin: (String, String) -> Unit, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Person, null, tint = Green, modifier = Modifier.size(60.dp))
        Spacer(Modifier.height(16.dp))
        Text("RaC Ghana account", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Navy)
        if (!token.isNullOrBlank()) {
            Text("You are signed in.", Modifier.padding(12.dp), color = Green)
            Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Sign out") }
        } else {
            Text("Sign in to manage your bookings.", Modifier.padding(12.dp), color = Color.Gray)
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onLogin(email.trim(), password) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Sign in") }
        }
    }
}

@Composable
fun EmptyState(title: String, description: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.DirectionsCar, null, tint = Green, modifier = Modifier.size(48.dp))
        Text(title, Modifier.padding(top = 12.dp), color = Navy, fontWeight = FontWeight.Bold)
        Text(description, Modifier.padding(vertical = 8.dp), color = Color.Gray)
        TextButton(onClick = onRetry) { Text("Try again") }
    }
}
