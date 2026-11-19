package com.racghana.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
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

private fun imageUrl(value: String): String {
    val clean = value.trim().replace("\\\\/", "/")
    return when {
        clean.startsWith("https://", true) || clean.startsWith("http://", true) -> clean
        clean.startsWith("//") -> "https:$clean"
        clean.startsWith("/") -> "https://racghana.com$clean"
        else -> "https://racghana.com/${clean.trimStart('/')}"
    }
}

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
    var authMessage by remember { mutableStateOf("") }

    suspend fun loadInitial() {
        loading = true
        try {
            // Load each endpoint sequentially to keep coroutine usage simple and reliable.
            val configsResult = RacApi.get("configs")
            val gatewaysResult = RacApi.get("gateways")
            val homeResult = RacApi.get("home-page")
            val searchResult = RacApi.get("car/search")
            configLoaded = configsResult.status in 200..299
            gateways = RacApi.rows(gatewaysResult).size
            val liveCars = RacApi.rows(searchResult).mapNotNull { parseCar(it) }
            cars = liveCars
            message = if (liveCars.isEmpty()) {
                "No vehicles were returned by the car search service (HTTP ${searchResult.status}). Pull to refresh or try again."
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
                    modifier = Modifier.padding(padding)
                )
                tab == "Home" -> HomeScreen(
                    cars = cars,
                    loading = loading,
                    message = message,
                    onRefresh = { scope.launch { loadInitial() } },
                    onCar = { selected = it },
                    onExplore = { tab = "Explore" },
                    onSearch = { location, from, to ->
                        scope.launch {
                            loading = true
                            tab = "Explore"
                            try {
                                val params = buildMap {
                                    if (location.isNotBlank()) put("location", location.trim())
                                    if (from.isNotBlank()) put("from", from.trim())
                                    if (to.isNotBlank()) put("to", to.trim())
                                }
                                val result = RacApi.get("car/search", params)
                                cars = RacApi.rows(result).mapNotNull { parseCar(it) }
                                message = if (result.status in 200..299) "${cars.size} matching vehicles found." else "Search failed (HTTP ${result.status})."
                            } catch (e: Exception) {
                                message = "Search error: ${e.localizedMessage ?: "Please try again."}"
                            } finally { loading = false }
                        }
                    },
                    modifier = Modifier.padding(padding)
                )
                tab == "Explore" -> ExploreScreen(
                    cars = cars,
                    loading = loading,
                    onRefresh = { scope.launch { loadInitial() } },
                    onCar = { selected = it },
                    modifier = Modifier.padding(padding)
                )
                tab == "Bookings" -> BookingScreen(token, onSignIn = { tab = "Profile" }, modifier = Modifier.padding(padding))
                else -> ProfileScreen(
                    token = token,
                    authMessage = authMessage,
                    loading = loading,
                    onLogin = { email, password ->
                        if (email.isBlank() || password.isBlank()) {
                            authMessage = "Enter your email and password."
                        } else scope.launch {
                            loading = true
                            authMessage = "Signing in…"
                            try {
                                val result = RacApi.post("auth/login", mapOf("email" to email.trim(), "password" to password))
                                val root = result.json()
                                val body = RacApi.unwrap(result)
                                val nested = body.optJSONObject("user")
                                val newToken = RacApi.firstText(body, "token", "access_token", "accessToken")
                                    .ifBlank { RacApi.firstText(root, "token", "access_token", "accessToken") }
                                if (result.status in 200..299 && newToken.isNotBlank()) {
                                    token = newToken
                                    prefs.edit().putString("token", newToken).apply()
                                    authMessage = "You are signed in."
                                } else {
                                    val errors = root.optJSONObject("errors")
                                    authMessage = RacApi.firstText(root, "message", "error")
                                        .ifBlank { RacApi.firstText(body, "message", "error") }
                                        .ifBlank { errors?.toString().orEmpty() }
                                        .ifBlank { "Sign in failed (HTTP ${result.status}). Check your details." }
                                }
                            } catch (e: Exception) {
                                authMessage = "Sign in error: ${e.localizedMessage ?: "Connection problem."}"
                            } finally { loading = false }
                        }
                    },
                    onRegister = { first, last, phone, email, password ->
                        if (listOf(first, last, email, location, password).any { it.isBlank() }) {
                            authMessage = "Complete all registration fields."
                        } else scope.launch {
                            loading = true
                            authMessage = "Creating your account…"
                            try {
                                val result = RacApi.post("auth/register", mapOf(
                                    "first_name" to first.trim(), "last_name" to last.trim(),
                                    "phone" to phone.trim(), "email" to email.trim(), "password" to password
                                ))
                                val root = result.json()
                                val body = RacApi.unwrap(result)
                                val newToken = RacApi.firstText(body, "token", "access_token", "accessToken")
                                    .ifBlank { RacApi.firstText(root, "token", "access_token", "accessToken") }
                                if (result.status in 200..299 && newToken.isNotBlank()) {
                                    token = newToken
                                    prefs.edit().putString("token", newToken).apply()
                                    authMessage = "Account created successfully."
                                } else {
                                    authMessage = RacApi.firstText(root, "message", "error")
                                        .ifBlank { RacApi.firstText(body, "message", "error") }
                                        .ifBlank { "Registration failed (HTTP ${result.status})." }
                                }
                            } catch (e: Exception) {
                                authMessage = "Registration error: ${e.localizedMessage ?: "Connection problem."}"
                            } finally { loading = false }
                        }
                    },
                    onLogout = {
                        scope.launch {
                            runCatching { RacApi.post("auth/logout", emptyMap<String, String>(), token) }
                            token = null
                            authMessage = "You have signed out."
                            prefs.edit().remove("token").apply()
                        }
                    },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

private fun firstImage(obj: JSONObject): String {
    val keys = listOf("image", "thumbnail", "image_url", "banner", "featured_image", "cover", "thumb", "image_path", "photo")
    for (key in keys) {
        when (val value = obj.opt(key)) {
            is String -> if (value.isNotBlank()) return value
            is JSONObject -> {
                val nested = RacApi.firstText(value, "url", "path", "src", "full", "large", "original")
                if (nested.isNotBlank()) return nested
            }
            is org.json.JSONArray -> if (value.length() > 0) {
                val first = value.opt(0)
                if (first is String && first.isNotBlank()) return first
                if (first is JSONObject) {
                    val nested = RacApi.firstText(first, "url", "path", "src", "full", "large", "original")
                    if (nested.isNotBlank()) return nested
                }
            }
        }
    }
    return ""
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
        image = firstImage(obj)
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
    onSearch: (String, String, String) -> Unit,
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
                SearchPanel(onSearch)
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
fun SearchPanel(onSearch: (String, String, String) -> Unit) {
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
        Button(onClick = { onSearch(location, from, to) }, Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Green), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Search cars", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CarCard(car: CarItem, onClick: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 7.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(175.dp).background(Color(0xFFE7ECEF)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.DirectionsCar, null, tint = Color(0xFF9AAAB4), modifier = Modifier.size(65.dp))
            if (car.image.isNotBlank()) AsyncImage(model = imageUrl(car.image), contentDescription = car.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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
fun CarDetailsScreen(car: CarItem, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var detailMessage by remember { mutableStateOf("Tap below to load the latest vehicle information and availability.") }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize().background(Canvas)) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
            Text("Vehicle details", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Navy)
        }
        Column(Modifier.weight(1f).padding(20.dp)) {
            Box(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFE7ECEF)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.DirectionsCar, null, tint = Color.Gray, modifier = Modifier.size(90.dp))
                if (car.image.isNotBlank()) AsyncImage(model = imageUrl(car.image), contentDescription = car.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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
        Button(onClick = {
            scope.launch {
                checking = true
                detailMessage = "Checking RaC Ghana…"
                try {
                    val detail = RacApi.get("car/detail/${car.id}")
                    val availability = RacApi.get("car/availability/${car.id}")
                    detailMessage = when {
                        detail.status !in 200..299 -> "Vehicle detail request returned HTTP ${detail.status}. ${RacApi.firstText(detail.json(), "message", "error")}".trim()
                        availability.status in 200..299 -> "Vehicle details and availability refreshed successfully."
                        else -> "Details loaded. Availability returned HTTP ${availability.status}."
                    }
                } catch (e: Exception) {
                    detailMessage = "Could not load details: ${e.localizedMessage ?: "Connection problem."}"
                } finally { checking = false }
            }
        }, enabled = !checking, modifier = Modifier.fillMaxWidth().padding(20.dp).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Green)) {
            Text(if (checking) "Checking…" else "Check live details & availability")
        }
    }
}

@Composable
fun BookingScreen(token: String?, onSignIn: () -> Unit, modifier: Modifier = Modifier) {
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
        if (token.isNullOrBlank()) Button(onClick = onSignIn, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Sign in to continue") }
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
fun ProfileScreen(token: String?, authMessage: String, loading: Boolean, onLogin: (String, String) -> Unit, onRegister: (String, String, String, String, String) -> Unit, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var acceptedTerms by remember { mutableStateOf(false) }
    var registering by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
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
            if (registering) {
                OutlinedTextField(firstName, { firstName = it }, Modifier.fillMaxWidth(), label = { Text("First name") }, singleLine = true)
                OutlinedTextField(lastName, { lastName = it }, Modifier.fillMaxWidth(), label = { Text("Last name") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Phone number") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = acceptedTerms, onCheckedChange = { acceptedTerms = it })
                    Text("I accept the Terms and Privacy Policy", fontSize = 12.sp, color = Color.Gray)
                }
            }
            Button(onClick = {
                if (registering) onRegister(firstName, lastName, if (acceptedTerms) phone else "", email, password) else onLogin(email.trim(), password)
            }, enabled = !loading, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green)) {
                Text(if (loading) "Please wait…" else if (registering) "Create account" else "Sign in")
            }
            TextButton(onClick = { registering = !registering }) {
                Text(if (registering) "Already have an account? Sign in" else "New to RaC? Create an account")
            }
        }
        if (authMessage.isNotBlank()) Text(authMessage, Modifier.fillMaxWidth().padding(top = 14.dp), color = if (authMessage.contains("failed", true) || authMessage.contains("error", true)) MaterialTheme.colorScheme.error else Green, fontSize = 13.sp)
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
