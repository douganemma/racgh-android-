package com.racghana.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions

private val Navy = Color(0xFF10243A)
private val Green = Color(0xFF0D8A68)
private val Canvas = Color(0xFFF5F7F8)
private data class Car(val name:String,val type:String,val seats:String,val transmission:String,val price:String,val image:String)
private val cars = listOf(
    Car("Toyota Land Cruiser Prado","SUV · Takoradi","7 seats","Automatic","GH₵ 1,200 / day","https://images.unsplash.com/photo-151 SUV"),
    Car("Nissan Navara Pickup","Pickup · Takoradi","4 seats","Manual","GH₵ 1,100 / day",""),
    Car("Toyota Hiace Bus","Van · Takoradi","14 seats","Manual","GH₵ 1,600 / day",""),
    Car("Toyota Hilux Pick-up (2017)","Pickup · Takoradi","5 seats","Manual","GH₵ 1,000 / day","")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RaCApp() }
    }
}

@Composable
fun RaCApp() {
    var tab by remember { mutableStateOf("Home") }
    var selected by remember { mutableStateOf<Car?>(null) }
    MaterialTheme(colorScheme = lightColorScheme(primary=Green, secondary=Navy, background=Canvas)) {
        Scaffold(containerColor=Canvas, bottomBar={
            NavigationBar(containerColor=Color.White) {
                listOf("Home","Explore","Bookings","Profile").forEach { item ->
                    NavigationBarItem(selected=tab==item,onClick={tab=item},icon={
                        Icon(when(item){"Home"->Icons.Default.Home;"Explore"->Icons.Default.Search;"Bookings"->Icons.Default.ConfirmationNumber;else->Icons.Default.Person},null)
                    },label={Text(item)})
                }
            }
        }) { padding ->
            if (selected != null) CarDetails(selected!!,{selected=null},{tab="Bookings";selected=null},Modifier.padding(padding))
            else when(tab) {
                "Home" -> HomeScreen(Modifier.padding(padding), onCar={selected=it}, onExplore={tab="Explore"})
                "Explore" -> ExploreScreen(Modifier.padding(padding),onCar={selected=it})
                "Bookings" -> InfoScreen("Your bookings","Your confirmed and requested trips will appear here.",Modifier.padding(padding))
                else -> InfoScreen("Welcome to RaC Ghana","Sign in or create an account to manage your trips and profile.",Modifier.padding(padding))
            }
        }
    }
}

@Composable
fun HomeScreen(modifier:Modifier,onCar:(Car)->Unit,onExplore:()->Unit) {
    LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(Navy).padding(horizontal=22.dp, vertical=24.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("RaC",fontSize=30.sp,fontWeight=FontWeight.ExtraBold,color=Color.White)
                    Text(" GHANA",fontSize=15.sp,fontWeight=FontWeight.Bold,color=Color(0xFF71D9B5))
                    Spacer(Modifier.weight(1f))
                    Text("Car rental made easy",color=Color.White.copy(.8f),fontSize=12.sp)
                }
                Spacer(Modifier.height(30.dp))
                Text("Go anywhere
with RaC.",fontSize=34.sp,lineHeight=39.sp,fontWeight=FontWeight.Bold,color=Color.White)
                Spacer(Modifier.height(8.dp))
                Text("Find the right ride for every journey.",color=Color.White.copy(.8f),fontSize=15.sp)
                Spacer(Modifier.height(22.dp))
                SearchPanel(onExplore)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=24.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Popular hire cars",fontSize=21.sp,fontWeight=FontWeight.Bold,color=Navy);Text("Handpicked rides for your next trip",fontSize=12.sp,color=Color.Gray) }
                TextButton(onClick=onExplore){Text("View all")}
            }
        }
        items(cars){ car -> CarCard(car){onCar(car)} }
        item {
            Column(Modifier.padding(20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFE2F3ED)).padding(20.dp)) {
                Text("Your journey, your way",fontSize=22.sp,fontWeight=FontWeight.Bold,color=Navy)
                Spacer(Modifier.height(6.dp))
                Text("Discover trusted rental options across Ghana.",color=Navy.copy(.75f))
                Spacer(Modifier.height(12.dp))
                Button(onClick=onExplore,colors=ButtonDefaults.buttonColors(containerColor=Green)){Text("Explore vehicles")}
            }
        }
    }
}

@Composable
fun SearchPanel(onSearch:()->Unit) {
    var location by remember { mutableStateOf("Accra, Ghana") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp)) {
        Text("Find your rental",fontWeight=FontWeight.Bold,fontSize=17.sp,color=Navy)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(location,{location=it},Modifier.fillMaxWidth(),label={Text("Pick-up location")},singleLine=true,leadingIcon={Icon(Icons.Default.LocationOn,null)})
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(from,{from=it},Modifier.weight(1f),label={Text("Pick-up date")},placeholder={Text("DD/MM/YYYY")},singleLine=true)
            OutlinedTextField(to,{to=it},Modifier.weight(1f),label={Text("Return date")},placeholder={Text("DD/MM/YYYY")},singleLine=true)
        }
        Button(onClick=onSearch,Modifier.fillMaxWidth().height(50.dp),colors=ButtonDefaults.buttonColors(containerColor=Green),shape=RoundedCornerShape(12.dp)){Icon(Icons.Default.Search,null);Spacer(Modifier.width(8.dp));Text("Search cars",fontWeight=FontWeight.Bold)}
    }
}

@Composable
fun CarCard(car:Car,onClick:()->Unit) {
    Column(Modifier.padding(horizontal=20.dp,vertical=7.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).clickable(onClick=onClick)) {
        Box(Modifier.fillMaxWidth().height(135.dp).background(Color(0xFFE7ECEF)),contentAlignment=Alignment.Center) {
            Icon(Icons.Default.DirectionsCar,null,tint=Color(0xFF9AAAB4),modifier=Modifier.size(65.dp))
            Surface(Modifier.align(Alignment.TopStart).padding(12.dp),color=Green,shape=RoundedCornerShape(6.dp)){Text(" FEATURED ",Modifier.padding(horizontal=8.dp,vertical=4.dp),color=Color.White,fontSize=10.sp,fontWeight=FontWeight.Bold)}
        }
        Column(Modifier.padding(15.dp)) {
            Text(car.type.uppercase(),fontSize=10.sp,color=Green,fontWeight=FontWeight.Bold)
            Text(car.name,fontSize=18.sp,fontWeight=FontWeight.Bold,color=Navy)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Label(Icons.Default.People,car.seats);Label(Icons.Default.Settings,car.transmission)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(car.price,fontSize=17.sp,fontWeight=FontWeight.Bold,color=Green)
                Spacer(Modifier.weight(1f));Text("View details  →",color=Navy,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
            }
        }
    }
}
@Composable fun Label(icon:androidx.compose.ui.graphics.vector.ImageVector,text:String){Row(verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Color.Gray,modifier=Modifier.size(16.dp));Spacer(Modifier.width(5.dp));Text(text,color=Color.Gray,fontSize=12.sp)}}

@Composable
fun ExploreScreen(modifier:Modifier,onCar:(Car)->Unit) {
    Column(modifier.fillMaxSize()) {
        Text("Explore cars",Modifier.padding(20.dp),fontSize=26.sp,fontWeight=FontWeight.Bold,color=Navy)
        LazyColumn { items(cars){CarCard(it){onCar(it)}} }
    }
}
@Composable
fun CarDetails(car:Car,onBack:()->Unit,onBook:()->Unit,modifier:Modifier=Modifier) {
    Column(modifier.fillMaxSize().background(Canvas)) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(16.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)};Text("Vehicle details",fontSize=19.sp,fontWeight=FontWeight.Bold,color=Navy)}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Box(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFE7ECEF)),contentAlignment=Alignment.Center){Icon(Icons.Default.DirectionsCar,null,tint=Color.Gray,modifier=Modifier.size(90.dp))}
            Spacer(Modifier.height(18.dp));Text(car.name,fontSize=25.sp,fontWeight=FontWeight.Bold,color=Navy)
            Text(car.type,color=Green,fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.height(15.dp));Row(horizontalArrangement=Arrangement.spacedBy(18.dp)){Label(Icons.Default.People,car.seats);Label(Icons.Default.Settings,car.transmission);Label(Icons.Default.Info,"4 doors")}
            Spacer(Modifier.height(22.dp));Text("Rental details",fontSize=18.sp,fontWeight=FontWeight.Bold,color=Navy)
            Text("Daily rate: ${car.price}. Final price and availability are confirmed by the rental provider.",Modifier.padding(top=8.dp),color=Color.DarkGray)
        }
        Button(onClick=onBook,Modifier.fillMaxWidth().padding(20.dp).height(52.dp),colors=ButtonDefaults.buttonColors(containerColor=Green)){Text("Request a booking")}
    }
}
@Composable
fun InfoScreen(title:String,description:String,modifier:Modifier=Modifier) {
    val uri= LocalUriHandler.current
    Column(modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
        Icon(Icons.Default.DirectionsCar,null,tint=Green,modifier=Modifier.size(60.dp))
        Spacer(Modifier.height(18.dp));Text(title,fontSize=25.sp,fontWeight=FontWeight.Bold,color=Navy)
        Text(description,Modifier.padding(vertical=12.dp),color=Color.Gray)
        Button(onClick={uri.openUri("https://racghana.com/")},colors=ButtonDefaults.buttonColors(containerColor=Green)){Text("Continue to RaC Ghana")}
    }
}
