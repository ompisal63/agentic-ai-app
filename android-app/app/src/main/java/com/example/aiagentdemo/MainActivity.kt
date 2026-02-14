package com.example.aiagentdemo

import android.content.Intent
import androidx.compose.ui.graphics.Brush
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import products
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.launch

/* -------------------- THEME COLORS -------------------- */
val HitachiRed = Color(0xFFB01E1E)
val HitachiBlack = Color(0xFF111111)
val HitachiGray = Color(0xFF6B6B6B)
/* -------------------- ACTIVITY -------------------- */
enum class OrderStage {
    RECEIVED,
    PACKING,
    OUT_FOR_DELIVERY,
    DELIVERED
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AIAgentScreen() }
    }
}
fun normalizeSpokenText(text: String): String {
    return text
        .replace(Regex("\\bto\\b", RegexOption.IGNORE_CASE), "2")
        .replace(Regex("\\btoo\\b", RegexOption.IGNORE_CASE), "2")
        .replace(Regex("\\bfor\\b", RegexOption.IGNORE_CASE), "4")
}

fun openUpiApp(context: android.content.Context, upiUrl: String): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse(upiUrl)
            setPackage("com.google.android.apps.nbu.paisa.user")
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}

fun recalculateTotal(
    products: List<Product>,
    onResult: (Int) -> Unit
) {
    ApiService.recalculateTotal(
        items = products.map {
            CartItem(
                name = it.name,
                quantity = it.quantity,
                price = it.price,
                recommended = false,
                reason = null,
                alternatives = emptyList()
            )
        },
        onResult = { total: Int ->
            onResult(total)
        },
        onError = {
            // ignore, keep last total
        }
    )
}


fun startProcessing(
    query: String,
    onStart: () -> Unit,
    onSuccess: (List<Product>, List<String>) -> Unit,
    onError: (String) -> Unit
)
{
    onStart()
    ApiService.fetchIntent(
        query = query,
        onResult = { intent ->

            val resolvedProducts = intent.items.mapNotNull { item ->

                val baseProduct = products.find {
                    it.name.equals(item.name, ignoreCase = true) ||
                            item.name.contains(it.name, ignoreCase = true) ||
                            it.name.contains(item.name, ignoreCase = true)
                } ?: return@mapNotNull null


                baseProduct.copy(
                    quantity = item.quantity,
                    isBrandAutoSelected = item.recommended,
                    recommendationReason = item.reason,
                )
            }

            if (resolvedProducts.isEmpty()) {
                onError("❌ Item not available right now")
            } else {
                onSuccess(resolvedProducts, intent.missing)
            }
        },
        onError = { error -> onError(error) }
    )
}
fun formatTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("hh:mm a",java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

/* -------------------- MAIN SCREEN -------------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIAgentScreen() {
    var showOtpOverlay by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var otpCardVisible by remember { mutableStateOf(true) }
    var selectedInlineMethod by remember { mutableStateOf("UPI") }
    var showGpayOption by remember { mutableStateOf(false) }
    var paymentNote by remember { mutableStateOf<String?>(null) }
    var agentState by remember { mutableStateOf<AgentState>(AgentState.Idle) }
    var input by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Ready to assist") }
    var cartProducts by remember { mutableStateOf<List<Product>>(emptyList()) }
    var orderCreatedAt by remember { mutableStateOf<Long?>(null) }
    val deliveryDurationMillis = 18 * 60 * 1000L // 18 minutes
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedPaymentMethod by remember { mutableStateOf<String?>(null) }
    var otp by remember { mutableStateOf("") }
    var otpDigits by remember { mutableStateOf(List(4) { "" }) }
    var backendOtp by remember { mutableStateOf<List<String>?>(null) }
    var isOtpVerifying by remember { mutableStateOf(false) }
    var otpSubmitted by remember { mutableStateOf(false) }
    var otpVerifiedSuccess by remember { mutableStateOf(false) }
    var showOtpBanner by remember { mutableStateOf(false) }
    var resendTimer by remember { mutableStateOf(30) }
    var canResend by remember { mutableStateOf(false) }
    var backendTotal by remember { mutableStateOf<Int?>(null) }
    val totalPrice by remember { derivedStateOf { backendTotal ?: 0 } }





    LaunchedEffect(agentState) {
        showOtpOverlay = agentState == AgentState.OtpVerification
    }

    LaunchedEffect(agentState) {
        if (agentState == AgentState.OtpVerification) {

            resendTimer = 30
            canResend = false


            // 🔁 RESET OTP STATE (CRITICAL)
            backendOtp = null
            otpSubmitted = false
            otpDigits = List(4) { "" }

            // ⏳ simulate backend generating OTP
            delay(1000)

            // 🔐 backend sends OTP (THIS is the "API response")
            backendOtp = listOf("4", "8", "2", "9")

            showOtpBanner = true
            delay(2500)
            showOtpBanner = false

        }

    }

    LaunchedEffect(agentState, backendOtp) {
        if (agentState == AgentState.OtpVerification) {
            resendTimer = 30
            canResend = false

            while (resendTimer > 0) {
                delay(1000)
                resendTimer--
            }
            canResend = true
        }
    }

    LaunchedEffect(backendOtp) {
        if (backendOtp != null) {

            backendOtp!!.forEachIndexed { index, digit ->
                delay(400)

                otpDigits = otpDigits.toMutableList().also {
                    it[index] = digit
                }
            }
        }
    }

    LaunchedEffect(otpDigits) {
        if (
            otpDigits.all { it.isNotBlank() } &&
            !otpSubmitted
        ) {
            otpSubmitted = true       // 🔒 prevent re-trigger
            isOtpVerifying = true
            delay(900)

            isOtpVerifying = false
            otpVerifiedSuccess = true

// 🟢 hold success state so user sees it
            delay(1200)

            otpCardVisible = false
            delay(350)

            showOtpOverlay = false
            otpVerifiedSuccess = false
            otpCardVisible = true
            orderCreatedAt = System.currentTimeMillis()

            // ✅ MARK PAYMENT AS SUCCESS IN BACKEND
            ApiService.recordPaymentEvent(
                userId = "demo-user",
                method = selectedPaymentMethod ?: "UPI",
                amount = totalPrice,
                status = "SUCCESS"
            )

            agentState = AgentState.PaymentProcessing

        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(60_000) // update every minute
        }
    }
    val remainingMinutes: Int? = orderCreatedAt?.let { start ->
        val endTime = start + deliveryDurationMillis
        val remaining = endTime - currentTime
        if (remaining > 0) {
            (remaining / (60 * 1000)).toInt() + 1
        } else {
            0
        }
    }

    val elapsedMillis: Long? = orderCreatedAt?.let { start ->
        currentTime - start
    }
    val orderStage: OrderStage? = elapsedMillis?.let { elapsed ->
        when {
            elapsed < 2 * 60 * 1000L -> OrderStage.RECEIVED
            elapsed < 7 * 60 * 1000L -> OrderStage.PACKING
            elapsed < deliveryDurationMillis -> OrderStage.OUT_FOR_DELIVERY
            else -> OrderStage.DELIVERED
        }
    }

    /* -------------------- VOICE -------------------- */
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()

            if (!spoken.isNullOrBlank()) {
                val cleanedText = normalizeSpokenText(spoken)

                input = cleanedText

                startProcessing(
                    cleanedText,
                    onStart = {
                        agentState = AgentState.Processing
                        statusText = "AI is adding your order to cart…"
                    },
                    onSuccess = { resolvedProducts, missing ->

                        cartProducts = resolvedProducts
                        agentState = AgentState.CartPreview

                        statusText =
                            if (missing.isNotEmpty()) {
                                "Some items are not available: ${missing.joinToString(", ")}"
                            } else {
                                "Here’s your cart"
                            }

                        // ✅ STEP 1B: Pre-fetch payment recommendation (ONLY HERE)
                        ApiService.fetchPaymentDecision(
                            userId = "demo-user",
                            items = resolvedProducts.map {
                                CartItem(
                                    name = it.name,
                                    quantity = it.quantity,
                                    price = it.price,
                                    recommended = false,
                                    reason = null,
                                    alternatives = emptyList()
                                )
                            },
                            onResult = { method, note, allowSwitch, total ->
                                selectedPaymentMethod = method
                                paymentNote = note
                                backendTotal = total
                            },
                            onError = {
                                paymentNote = "⚠️ Unable to calculate total"
                            }
                        )
                    },


                    onError = { error ->
                        statusText = error
                        agentState = AgentState.Idle
                    }
                )
            }

        }
    }

    /* -------------------- PAYMENT PROCESSING -------------------- */
    if (agentState == AgentState.PaymentProcessing) {

        LaunchedEffect(Unit) {
            delay(2600)
            agentState = AgentState.PaymentSuccess
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // 🔴 Animated Brand Ring
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp,
                    color = HitachiRed
                )

                Spacer(Modifier.height(28.dp))

                Text(
                    text = "Processing your payment",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HitachiBlack
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "This usually takes a few seconds",
                    fontSize = 13.sp,
                    color = HitachiGray
                )

                Spacer(Modifier.height(18.dp))

                // 🔐 Trust indicator (VERY important psychologically)
                Text(
                    text = "🔒 Secured by bank-grade encryption",
                    fontSize = 12.sp,
                    color = HitachiGray
                )
            }
        }
        return
    }

    /* -------------------- PAYMENT SUCCESS -------------------- */
    if (agentState == AgentState.PaymentSuccess) {

        LaunchedEffect(Unit) {
            delay(2000)
            agentState = AgentState.OrderComplete
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // 🟢 Success circle
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color(0xFF22C55E), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✓",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(22.dp))

                Text(
                    text = "Payment successful",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = HitachiBlack
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Preparing your order summary",
                    fontSize = 13.sp,
                    color = HitachiGray
                )
            }
        }
        return
    }

    /* -------------------- ORDER COMPLETE (ENHANCED UI ONLY) -------------------- */
    if (agentState == AgentState.OrderComplete) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {

            // Success header card
            val heroScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.6f,
                    stiffness = 300f
                ),
                label = "heroScale"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                HitachiRed,
                                Color(0xFF8F1A1A) // deeper red for richness
                            )
                        )
                    )
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color.White.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "✓",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = "Order placed successfully",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "Thank you for shopping with Hitachi",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Order meta
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    // LEFT: Order ID
                    Column {
                        Text(
                            text = "Order ID",
                            fontSize = 12.sp,
                            color = HitachiGray
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "NX-${System.currentTimeMillis() % 100000}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = HitachiBlack
                        )
                    }

                    // RIGHT: Order Time
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Order time",
                            fontSize = 12.sp,
                            color = HitachiGray
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Just now",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = HitachiBlack
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            OrderTimeline(
                currentStage = orderStage,
                remainingMinutes = remainingMinutes
            )


            Spacer(Modifier.height(24.dp))

            // Order summary
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFCFCFD)
                ),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {

                Column(Modifier.padding(16.dp)) {
                    Text("🛒 Order Summary", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))

                    cartProducts.forEach { product ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${product.name} × ${product.quantity}")
                        }
                    }

                    Divider(Modifier.padding(vertical = 12.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total", fontWeight = FontWeight.Bold)
                        Text(
                            "₹$totalPrice",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = HitachiRed
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Payment details
            Text("💳 Payment Details", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Payment Status", color = HitachiGray)
                        Text("Successful", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Payment Mode", color = HitachiGray)
                        Text(
                            when (selectedPaymentMethod) {
                                "GPAY" -> "Google Pay"
                                "CARD" -> "Card"
                                else -> "UPI"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = HitachiRed),
                onClick = {
                    input = ""
                    cartProducts = emptyList()
                    statusText = "Ready to Assist"
                    agentState = AgentState.Idle
                }
            ) {
                Text("Order Again")
            }
        }
        return
    }

    /* -------------------- MAIN UI -------------------- */
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEEF1F6))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFEEF1F6))
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 100.dp
                )
        ) {


            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    // 🔙 Back button (ONLY visible in cart)
                    if (agentState == AgentState.CartPreview) {
                        IconButton(
                            onClick = {
                                cartProducts = emptyList()
                                agentState = AgentState.Idle
                                statusText = "Ready to assist"
                            }
                        ) {
                            Text(
                                "←",
                                fontSize = 22.sp,
                                color = HitachiBlack
                            )
                        }

                        Spacer(Modifier.width(8.dp))
                    }

                    Column {
                        Text(
                            text = "Hitachi Store",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = HitachiBlack
                        )

                        Spacer(Modifier.height(2.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(18.dp)
                                    .height(2.dp)
                                    .background(HitachiRed, RoundedCornerShape(2.dp))
                            )

                            Spacer(Modifier.width(8.dp))

                            Text(
                                text = "AI-powered smart shopping",
                                fontSize = 13.sp,
                                color = HitachiGray
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))


            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF8FAFC)
                ),
                elevation = CardDefaults.cardElevation(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    placeholder = { Text("Search or say what you need") },
                    trailingIcon = {
                        IconButton(onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE,
                                    Locale.getDefault()
                                )
                            }
                            voiceLauncher.launch(intent)
                        }) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Mic",
                                tint = HitachiRed
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = HitachiRed
                    )
                )
            }


            Spacer(Modifier.height(12.dp))

            if (agentState == AgentState.Idle || agentState == AgentState.Processing) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HitachiRed),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 2.dp
                    ),
                    onClick = {
                        startProcessing(
                            input,
                            onStart = {
                                agentState = AgentState.Processing
                                statusText = "AI is adding your order to cart…"
                            },
                            onSuccess = { resolvedProducts, missing ->

                                cartProducts = resolvedProducts
                                agentState = AgentState.CartPreview

                                statusText =
                                    if (missing.isNotEmpty()) {
                                        "Some items are not available: ${missing.joinToString(", ")}"
                                    } else {
                                        "Here’s your cart"
                                    }

                                // ✅ STEP 1B (TEXT FLOW ALSO) — THIS WAS MISSING
                                ApiService.fetchPaymentDecision(
                                    userId = "demo-user",
                                    items = resolvedProducts.map {
                                        CartItem(
                                            name = it.name,
                                            quantity = it.quantity,
                                            price = it.price,
                                            recommended = false,
                                            reason = null,
                                            alternatives = emptyList()
                                        )
                                    },
                                    onResult = { method, note, allowSwitch, total ->
                                        selectedPaymentMethod = method
                                        paymentNote = note
                                        backendTotal = total // 🔥 THIS FIXES ₹0
                                        // 👇 ADD THIS
                                        showGpayOption = total < 1000 || total >= 1000
                                    },
                                    onError = {
                                        paymentNote = "⚠️ Unable to calculate total"
                                    }
                                )
                            },
                            onError = { error ->
                                statusText = error
                                agentState = AgentState.Idle
                            }
                        )
                    }
                ) {
                    Text(
                        "Send",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

            }

            Spacer(Modifier.height(12.dp))
            if (agentState == AgentState.CartPreview ||
                agentState == AgentState.Processing ||
                agentState == AgentState.Idle
            ) {
                Text(
                    statusText,
                    color = HitachiGray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            AnimatedVisibility(
                visible = agentState == AgentState.CartPreview,
                enter = slideInVertically(
                    initialOffsetY = { it / 3 }
                ) + fadeIn()
            ) {
                Column {


                    Spacer(Modifier.height(20.dp))

                    // 1️⃣ AI CONFIRMATION CARD

                    Spacer(Modifier.height(16.dp))

                    // 2️⃣ CART ITEMS CARD
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {

                            Text(
                                "Your Items",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HitachiBlack
                            )

                            Spacer(Modifier.height(12.dp))

                            cartProducts.forEachIndexed { index, product ->

                                ZeptoCartCard(
                                    product = product,
                                    onIncrease = {
                                        val updatedCart = cartProducts.toMutableList().also {
                                            it[index] = it[index].copy(
                                                quantity = it[index].quantity + 1
                                            )
                                        }

                                        cartProducts = updatedCart

                                        recalculateTotal(updatedCart) { total: Int ->
                                            backendTotal = total
                                        }
                                    },
                                    onDecrease = {
                                        if (product.quantity > 1) {
                                            val updatedCart = cartProducts.toMutableList().also {
                                                it[index] = it[index].copy(
                                                    quantity = it[index].quantity - 1
                                                )
                                            }

                                            cartProducts = updatedCart

                                            recalculateTotal(updatedCart) { total: Int ->
                                                backendTotal = total
                                            }

                                        }
                                    },
                                )
                                val alternatives = products.filter {
                                    it.category == product.category &&
                                            it.name != product.name
                                }


                                if (alternatives.isNotEmpty()) {
                                    Text(
                                        text = if (product.isBrandAutoSelected)
                                            "Other available options"
                                        else
                                            "Available alternatives",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = HitachiGray
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateContentSize(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(0xFFF6F7FB)
                                        ),
                                        elevation = CardDefaults.cardElevation(2.dp)
                                    ) {
                                        Column(Modifier.padding(12.dp)) {


                                            Spacer(Modifier.height(8.dp))

                                            alternatives.forEach { alt ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {

                                                    Image(
                                                        painter = painterResource(alt.imageRes),
                                                        contentDescription = alt.name,
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(Color(0xFFF3F4F6)),
                                                        contentScale = ContentScale.Fit
                                                    )

                                                    Spacer(Modifier.width(10.dp))

                                                    Column(Modifier.weight(1f)) {
                                                        Text(
                                                            alt.name,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        Text(
                                                            "₹${alt.price}",
                                                            fontSize = 12.sp,
                                                            color = HitachiGray
                                                        )
                                                    }

                                                    TextButton(onClick = {

                                                        val updatedCart = cartProducts.map { current ->
                                                            if (current.name == product.name) {
                                                                alt.copy(
                                                                    quantity = current.quantity,
                                                                    isBrandAutoSelected = false,
                                                                    recommendationReason = null
                                                                )
                                                            } else {
                                                                current
                                                            }
                                                        }

                                                        cartProducts = updatedCart

                                                        // 🔥 BACKEND RE-CALC (CRITICAL)
                                                        ApiService.recalculateTotal(
                                                            items = updatedCart.map {
                                                                CartItem(
                                                                    name = it.name,
                                                                    quantity = it.quantity,
                                                                    price = it.price,
                                                                    recommended = false,
                                                                    reason = null,
                                                                    alternatives = emptyList()
                                                                )
                                                            },
                                                            onResult = { total ->
                                                                backendTotal = total
                                                            },
                                                            onError = {
                                                                // optional: keep last total
                                                            }
                                                        )

                                                    }) {
                                                        Text("Select", color = HitachiRed)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }


                                // ✅ Divider BETWEEN items (not after last)
                                if (index != cartProducts.lastIndex) {
                                    Divider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = Color(0xFFE5E7EB)
                                    )
                                }
                            }

                        }
                    }

                    Spacer(Modifier.height(28.dp))

                    // 3️⃣ PRICE SUMMARY CARD
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        elevation = CardDefaults.cardElevation(3.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Subtotal", color = HitachiGray)
                                Text(
                                    if (backendTotal == null) "Calculating…" else "₹$totalPrice"
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Delivery", color = HitachiGray)
                                Text("₹0")
                            }

                            Divider(Modifier.padding(vertical = 12.dp))

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Total",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "₹$totalPrice",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = HitachiRed
                                )
                            }
                        }
                    }


                    // 4️⃣ PAYMENT GATEWAY (MULTI OPTION)

// 🔹 Recommended method UI
                    when (selectedPaymentMethod) {

                        "UPI" -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 20.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                                elevation = CardDefaults.cardElevation(4.dp)
                            ) {
                                Column(Modifier.padding(20.dp)) {

                                    // AI note
                                    if (paymentNote != null) {
                                        Card(
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F7F3))
                                        ) {
                                            Text(
                                                paymentNote!!,
                                                modifier = Modifier.padding(12.dp),
                                                fontSize = 13.sp,
                                                color = Color(0xFF1E7F5C)
                                            )
                                        }
                                        Spacer(Modifier.height(16.dp))
                                    }

                                    Text(
                                        "Pay using:",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = HitachiBlack
                                    )

                                    Spacer(Modifier.height(16.dp))
                                    // 🔘 UPI ID CARD
                                    InlinePaymentCard(
                                        selected = selectedInlineMethod == "UPI",
                                        onClick = { selectedInlineMethod = "UPI" }
                                    ) {
                                        Column {
                                            Text("UPI ID", fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "hitachiuser.pay@upi",
                                                fontSize = 13.sp,
                                                color = HitachiGray
                                            )
                                        }
                                    }

// 🔘 GOOGLE PAY CARD
                                    InlinePaymentCard(
                                        selected = selectedInlineMethod == "GPAY",
                                        onClick = { selectedInlineMethod = "GPAY" }
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.gpay_logo),
                                            contentDescription = "Google Pay",
                                            modifier = Modifier
                                                .size(32.dp)
                                                .align(Alignment.CenterVertically)

                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                "Google Pay",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                "Fast & secure",
                                                fontSize = 12.sp,
                                                color = HitachiGray
                                            )
                                        }

                                    }

                                    Spacer(Modifier.height(12.dp))

                                    TextButton(
                                        onClick = {
                                            selectedPaymentMethod = "CARD"
                                            paymentNote = null   // 🔥 CLEAR AI NOTE
                                        },
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    ) {
                                        Text("Use other payment options", color = HitachiRed)
                                    }

                                }
                            }
                        }


                        "CARD" -> {
                            PaymentConfirmCardUI(
                                title = "Pay using:",
                                lines = listOf(
                                    "Card Number: **** **** **** 4589",
                                    "Expiry: 09/27",
                                    "CVV: ***",
                                    "Card Holder: HITACHI USER"
                                ),
                                paymentNote = null, // ❌ NEVER show AI note here
                                onSwitchMethod = {
                                    selectedPaymentMethod = "UPI"
                                },
                                switchText = "Use UPI / Google Pay instead"
                            )

                        }
                    }

                    // -------------------- STICKY PROCEED BAR --------------------
                    if (agentState == AgentState.CartPreview) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            shape = RoundedCornerShape(28.dp),
                            elevation = CardDefaults.cardElevation(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {

                                Column {
                                    Text(
                                        text = "Total",
                                        fontSize = 12.sp,
                                        color = HitachiGray
                                    )
                                    Text(
                                        text = "₹$totalPrice",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HitachiRed
                                    )
                                }

                                Button(
                                    modifier = Modifier.height(54.dp),
                                    enabled = true,
                                    shape = RoundedCornerShape(26.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = HitachiRed),
                                    elevation = ButtonDefaults.buttonElevation(
                                        defaultElevation = 8.dp,
                                        pressedElevation = 2.dp
                                    ),
                                    onClick = {

                                        // 🔐 record brand events
                                        cartProducts.forEach { product ->
                                            ApiService.recordBrandEvent(
                                                userId = "demo-user",
                                                category = product.category,
                                                product = product.name
                                            )
                                        }

                                        ApiService.recordPaymentEvent(
                                            userId = "demo-user",
                                            method = selectedPaymentMethod ?: "UPI",
                                            amount = totalPrice,
                                            status = "INITIATED"
                                        )

                                        // 🟢 BRANCHING LOGIC (FINAL & CLEAN)
                                        if (
                                            selectedPaymentMethod == "UPI" &&
                                            selectedInlineMethod == "GPAY"
                                        ) {
                                            // Google Pay flow (NO OTP)
                                            ApiService.createOrder(
                                                userId = "demo-user",
                                                items = cartProducts.map {
                                                    CartItem(
                                                        name = it.name,
                                                        quantity = it.quantity,
                                                        price = it.price,
                                                        recommended = false,
                                                        reason = null,
                                                        alternatives = emptyList()
                                                    )
                                                },
                                                onResult = { orderId, _ ->
                                                    ApiService.fetchUpiIntent(
                                                        orderId = orderId,
                                                        onResult = { upiUrl ->
                                                            val opened = openUpiApp(context, upiUrl)
                                                            if (opened) {
                                                                agentState =
                                                                    AgentState.PaymentProcessing
                                                            } else {
                                                                agentState =
                                                                    AgentState.OtpVerification
                                                            }
                                                        },
                                                        onError = {
                                                            agentState = AgentState.OtpVerification
                                                        }
                                                    )
                                                },
                                                onError = {
                                                    agentState = AgentState.OtpVerification
                                                }
                                            )
                                        } else {
                                            // UPI ID / Card → OTP flow
                                            agentState = AgentState.OtpVerification
                                        }
                                    }

                                        ) {
                                    Text(
                                        text = "Confirm Order & Pay",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                            }

                        }
                    }

                }
            }

            // -------------------- ALL PRODUCTS (DYNAMIC, BACKEND-DRIVEN) --------------------
            if (agentState == AgentState.Idle && cartProducts.isEmpty()) {

                Spacer(Modifier.height(24.dp))

                val popularNearYou = products.filter {
                    it.category.lowercase() in listOf("bread", "milk", "egg")
                }.take(6)

                val milkAndCurd = products.filter {
                    it.category.lowercase() in listOf("milk", "curd")
                }

                val groceries = products.filter {
                    it.category.lowercase() in listOf(
                        "milk",
                        "curd",
                        "bread",
                        "egg",
                        "rice",
                        "oil",
                        "sugar"
                    )
                }


                val highValueItems = products.filter {
                    it.price >= 500
                }

                HomeSection(
                    title = "Popular near you",
                    subtitle = "Fast moving items in your area",
                    items = popularNearYou
                )

                HomeSection(
                    title = "Milk & Curd",
                    subtitle = "Fresh dairy delivered daily",
                    items = milkAndCurd
                )

                HomeSection(
                    title = "Groceries & Essentials",
                    subtitle = "Daily needs for your home",
                    items = groceries
                )

                HomeSection(
                    title = "High value items",
                    subtitle = "Big purchases, delivered safely",
                    items = highValueItems
                )

            }



            /* ---------------- OTP OVERLAY SHELL ---------------- */
            if (showOtpOverlay) {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {

                    val otpScale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (otpCardVisible) 1f else 0.92f,
                        animationSpec = androidx.compose.animation.core.tween(300),
                        label = "otpScale"
                    )

                    val otpAlpha by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (otpCardVisible) 1f else 0f,
                        animationSpec = androidx.compose.animation.core.tween(300),
                        label = "otpAlpha"
                    )


                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .graphicsLayer {
                                scaleX = otpScale
                                scaleY = otpScale
                                alpha = otpAlpha
                            },
                        shape = RoundedCornerShape(26.dp),
                        elevation = CardDefaults.cardElevation(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {

                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {

                            // 🔐 Premium header (Zepto-style)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {

                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            HitachiRed,
                                            RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "H",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = "OTP Verification",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = HitachiBlack
                                    )

                                    Spacer(Modifier.height(2.dp))

                                    Text(
                                        text = "Code sent to •••• 4532",
                                        fontSize = 13.sp,
                                        color = HitachiGray
                                    )
                                }
                            }



                            Spacer(Modifier.height(12.dp))



                            Spacer(Modifier.height(20.dp))

                            // 🔢 OTP AREA (DIGITS → SUCCESS)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 22.dp, bottom = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {

                                if (otpVerifiedSuccess) {

                                    // ✅ SUCCESS STATE (REAL BANK FEEL)
                                    Card(
                                        modifier = Modifier.size(72.dp),
                                        shape = CircleShape,
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(0xFF22C55E)
                                        ),
                                        elevation = CardDefaults.cardElevation(8.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Text(
                                                "✓",
                                                fontSize = 36.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }

                                } else {

                                    // 🔢 OTP BOXES (NORMAL STATE)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        otpDigits.forEach { digit ->

                                            val scale by androidx.compose.animation.core.animateFloatAsState(
                                                targetValue = if (digit.isNotEmpty()) 1.04f else 1f,
                                                animationSpec = androidx.compose.animation.core.tween(
                                                    180
                                                ),
                                                label = "otpDigitScale"
                                            )

                                            Card(
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .graphicsLayer {
                                                        scaleX = scale
                                                        scaleY = scale
                                                    },
                                                shape = RoundedCornerShape(14.dp),
                                                elevation = CardDefaults.cardElevation(
                                                    if (digit.isNotEmpty()) 6.dp else 3.dp
                                                ),
                                                border = BorderStroke(
                                                    1.6.dp,
                                                    if (digit.isNotEmpty())
                                                        HitachiRed
                                                    else
                                                        HitachiRed.copy(alpha = 0.35f)
                                                ),
                                                colors = CardDefaults.cardColors(containerColor = Color.White)
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    Text(
                                                        text = digit,
                                                        fontSize = 21.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = HitachiBlack
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                        }




                        if (isOtpVerifying) {
                            Spacer(Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = HitachiRed
                                )

                                Spacer(Modifier.width(10.dp))

                                Text(
                                    text = "Verifying…",
                                    fontSize = 13.sp,
                                    color = HitachiGray
                                )
                            }
                        }



                        Spacer(Modifier.height(16.dp))

                        TextButton(
                            enabled = canResend,
                            onClick = {
                                if (!canResend) return@TextButton

                                // 1️⃣ reset UI
                                otpDigits = List(4) { "" }
                                otpSubmitted = false
                                isOtpVerifying = false

                                // 2️⃣ restart resend timer
                                resendTimer = 30
                                canResend = false

                                // 3️⃣ simulate backend generating NEW OTP
                                backendOtp = null

                                // small delay to feel real
                                kotlinx.coroutines.GlobalScope.launch {
                                    delay(700)

                                    backendOtp = List(4) {
                                        (0..9).random().toString()
                                    }

                                    // 4️⃣ show notification banner again
                                    showOtpBanner = true
                                    delay(2000)
                                    showOtpBanner = false
                                }
                            }

                        )
                        {
                            Text(
                                text = if (canResend)
                                    "Resend OTP"
                                else
                                    "Resend OTP in ${resendTimer}s",
                                fontSize = 13.sp,
                                color = if (canResend) HitachiRed else HitachiGray
                            )
                        }


                    }
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            AnimatedVisibility(
                visible = showOtpBanner && agentState == AgentState.OtpVerification,
                enter = slideInVertically(
                    initialOffsetY = { -it }
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { -it }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {

                Card(
                    modifier = Modifier.padding(16.dp),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(HitachiRed, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "H",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Hitachi",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HitachiBlack
                            )
                            Text(
                                text = "Your OTP is ${backendOtp?.joinToString("") ?: ""}",
                                fontSize = 13.sp,
                                color = HitachiGray
                            )
                        }
                    }
                }
            }
        }

    }
}
/* -------------------- PAYMENT CONFIRM UPI -------------------- */
@Composable
fun PaymentConfirmCardUI(
    title: String,
    lines: List<String>,
    paymentNote: String?,
    onSwitchMethod: (() -> Unit)? = null,
    switchText: String = ""
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
        elevation = CardDefaults.cardElevation(4.dp)
    )

    {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {

            // ⚡ AI recommendation text (RULE-BASED)
            if (paymentNote != null) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF0F7F3)
                    )
                ) {
                    Text(
                        text = paymentNote,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp,
                        color = Color(0xFF1E7F5C)
                    )
                }

                Spacer(Modifier.height(16.dp))
            }

            // 🔐 Header
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = HitachiBlack
            )

            Spacer(Modifier.height(12.dp))

            // 📄 Payment details box
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(6.dp), // ⬅️ stronger depth
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.8.dp, // ⬅️ slightly thicker
                        color = HitachiRed.copy(alpha = 0.55f), // ⬅️ darker red
                        shape = RoundedCornerShape(14.dp)
                    )
            )
            {
                Column(Modifier.padding(14.dp)) {
                    lines.forEach { line ->
                        val parts = line.split(":", limit = 2)

                        Text(
                            buildAnnotatedString {
                                // LABEL (bold + darker)
                                withStyle(
                                    style = SpanStyle(
                                        fontWeight = FontWeight.SemiBold,
                                        color = HitachiBlack
                                    )
                                ) {
                                    append(parts[0])
                                    append(": ")
                                }

                                // VALUE (normal + muted)
                                if (parts.size > 1) {
                                    withStyle(
                                        style = SpanStyle(
                                            fontWeight = FontWeight.Normal,
                                            color = HitachiGray
                                        )
                                    ) {
                                        append(parts[1].trim())
                                    }
                                }
                            },
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            // 🔁 Switch option (ONLY action here)
            if (onSwitchMethod != null) {
                Spacer(Modifier.height(14.dp))
                TextButton(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = onSwitchMethod
                ) {
                    Text(
                        text = switchText,
                        fontSize = 14.sp,
                        color = HitachiRed
                    )
                }
            }
        }
    }
}


@Composable
fun InlinePaymentCard(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.5.dp,
            if (selected) HitachiRed else Color(0xFFE5E7EB)
        ),
        elevation = CardDefaults.cardElevation(
            if (selected) 6.dp else 2.dp
        ),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Spacer(Modifier.width(10.dp))
            content()
        }
    }
}




/* -------------------- CART CARD -------------------- */
@Composable
fun ZeptoCartCard(
    product: Product,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {

            // 🖼 Product Image
            Image(
                painter = painterResource(product.imageRes),
                contentDescription = product.name,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF3F4F6)),
                contentScale = ContentScale.Fit
            )


            Spacer(Modifier.width(14.dp))

            // 📄 Product Info
            Column(
                modifier = Modifier
                    .weight(1f)
            ) {

                // 🧾 Title + Price row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = product.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HitachiBlack
                    )

                    Text(
                        "₹${product.price * product.quantity}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = HitachiBlack
                    )
                }

                Spacer(Modifier.height(4.dp))

                // 🧠 AI explanation
                if (
                    product.isBrandAutoSelected &&
                    product.recommendationReason != null &&
                    product.category.lowercase() != "egg"
                ) {

                    Text(
                        text = product.recommendationReason,
                        fontSize = 12.sp,
                        color = HitachiGray,
                        lineHeight = 16.sp
                    )
                }


                // ➕➖ Quantity row (separate, clean)
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDecrease) { Text("−") }
                    Text(
                        product.quantity.toString(),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    IconButton(onClick = onIncrease) { Text("+") }
                }
            }
        }
    }
}

/* -------------------- INTENT HANDLER -------------------- */


@Composable
fun ProductPreviewCard(
    name: String,
    price: String,
    imageRes: Int
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .height(190.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFEDF1F7) // premium surface
        ),
        elevation = CardDefaults.cardElevation(10.dp)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        color = Color(0xFFF6F7F9), // neutral image surface
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {

                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = name,
                    modifier = Modifier.size(96.dp),
                    contentScale = ContentScale.Fit
                )

            }

            Column {
                Text(
                    text = name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = HitachiBlack,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "₹",
                        fontSize = 12.sp,
                        color = HitachiBlack
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = price.replace("₹", ""),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = HitachiBlack
                    )
                }
            }
        }
    }
}

@Composable
fun OrderTimeline(
    currentStage: OrderStage?,
    remainingMinutes: Int?
) {
    val stages = listOf(
        OrderStage.RECEIVED to "Order received",
        OrderStage.PACKING to "Being packed",
        OrderStage.OUT_FOR_DELIVERY to "Out for delivery",
        OrderStage.DELIVERED to "Delivered"
    )

    Column {
        Text(
            text = "Order status",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = HitachiBlack
        )

        Spacer(Modifier.height(12.dp))

        stages.forEach { (stage, label) ->
            val isCompleted =
                currentStage != null && stage.ordinal < currentStage.ordinal
            val isActive = currentStage == stage
            val stageTime: String? =
                if (currentStage != null && stage.ordinal <= currentStage.ordinal) {
                    val minutesOffset = when (stage) {
                        OrderStage.RECEIVED -> 0
                        OrderStage.PACKING -> 2
                        OrderStage.OUT_FOR_DELIVERY -> 7
                        OrderStage.DELIVERED -> 18
                    }
                    formatTime(
                        System.currentTimeMillis() - ((remainingMinutes ?: 0) * 60_000L) +
                                (minutesOffset * 60_000L)
                    )
                } else null

            Row(
                modifier = Modifier.padding(vertical = 6.dp),
                verticalAlignment = Alignment.Top
            ) {

                // LEFT: DOT + CONNECTOR
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // ● Dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                when {
                                    isCompleted -> Color(0xFF22C55E)
                                    isActive -> HitachiRed
                                    else -> Color(0xFFE5E7EB)
                                },
                                CircleShape
                            )
                    )

                    // │ Connector line (except last)
                    if (stage != OrderStage.DELIVERED) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(28.dp)
                                .background(
                                    if (isCompleted) Color(0xFF22C55E)
                                    else Color(0xFFE5E7EB)
                                )
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                // RIGHT: Label
                Column {

                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isActive || isCompleted) HitachiBlack else HitachiGray
                    )

                    if (stageTime != null) {
                        Text(
                            text = "at $stageTime",
                            fontSize = 12.sp,
                            color = HitachiGray
                        )
                    }
                }
            }
        }

        if (remainingMinutes != null && remainingMinutes > 0) {

            val pulse by androidx.compose.animation.core.animateFloatAsState(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(
                        durationMillis = 1200,
                        easing = androidx.compose.animation.core.LinearEasing
                    ),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "etaPulse"
            )

            Spacer(Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF0F7F3)
                ),
                modifier = Modifier.graphicsLayer {
                    alpha = 0.85f + (pulse * 0.15f)
                }
            ) {
                Text(
                    text = "Arriving in ~$remainingMinutes min",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    fontSize = 13.sp,
                    color = Color(0xFF1E7F5C),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
@Composable
fun HomeSection(
    title: String,
    subtitle: String,
    items: List<Product>
) {

    if (items.isEmpty()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = HitachiBlack
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = HitachiGray
            )

            Spacer(Modifier.height(12.dp))


            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(items) { product ->
                    ProductPreviewCard(
                        name = product.name,
                        price = "₹${product.price}",
                        imageRes = product.imageRes
                    )
                }
            }
        }
    }
}
