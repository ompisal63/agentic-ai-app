        package com.example.aiagentdemo
        import android.util.Log
        import okhttp3.Call
        import okhttp3.Callback
        import okhttp3.MediaType.Companion.toMediaType
        import okhttp3.OkHttpClient
        import okhttp3.Request
        import okhttp3.RequestBody.Companion.toRequestBody
        import okhttp3.Response
        import org.json.JSONObject
        import java.io.IOException
        import java.security.SecureRandom
        import java.security.cert.X509Certificate
        import javax.net.ssl.SSLContext
        import javax.net.ssl.TrustManager
        import javax.net.ssl.X509TrustManager

        object ApiService {

            private val client = OkHttpClient()

            //private val client = getUnsafeOkHttpClient()
            private val JSON = "application/json; charset=utf-8".toMediaType()


            fun requestOtp(
                orderId: String,
                userId: String,
                onSuccess: () -> Unit,
                onError: (String) -> Unit
            ) {
                val url = "http://10.0.2.2:3000/payment/request-otp"

                val jsonBody = JSONObject().apply {
                    put("orderId", orderId)
                    put("userId", userId)
                }

                val body = jsonBody.toString().toRequestBody(JSON)

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                client.newCall(request).enqueue(object : Callback {

                    override fun onFailure(call: Call, e: IOException) {
                        onError(e.message ?: "OTP request failed")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.close()
                        onSuccess() // ✅ OTP generated on backend
                    }
                })
            }

            fun verifyOtp(
                orderId: String,
                otp: String,
                onSuccess: () -> Unit,
                onError: (String) -> Unit
            ) {
                val url = "http://10.0.2.2:3000/payment/verify-otp"

                val jsonBody = JSONObject().apply {
                    put("orderId", orderId)
                    put("otp", otp)
                }

                val body = jsonBody.toString().toRequestBody(JSON)

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                client.newCall(request).enqueue(object : Callback {

                    override fun onFailure(call: Call, e: IOException) {
                        onError(e.message ?: "OTP verification failed")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()

                        if (!response.isSuccessful || responseBody == null) {
                            onError("Invalid OTP")
                            return
                        }

                        try {
                            val json = JSONObject(responseBody)
                            if (json.getString("status") == "SUCCESS") {
                                onSuccess()
                            } else {
                                onError("OTP invalid")
                            }
                        } catch (e: Exception) {
                            onError("OTP parse error")
                        }
                    }
                })
            }

            fun fetchIntent(
                query: String,
                onResult: (AgentIntent) -> Unit,
                onError: (String) -> Unit
            ) {
                val url = "http://172.20.10.3:3000/chat"

                val jsonBody = JSONObject()
                jsonBody.put("text", query)
                Log.d("@@ChatBody", jsonBody.toString())

                val body = jsonBody.toString().toRequestBody(JSON)

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    //            .get()
                    .build()

                Log.d("@@RequestBody", request.toString())

                client.newCall(request).enqueue(object : Callback {

                    override fun onFailure(call: Call, e: IOException) {
                        onError(e.message ?: "Network error")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()
                        Log.d("@@SearchResponse", responseBody ?: "")

                        if (responseBody == null) {
                            onError("Empty response")
                            return
                        }

                        if (!responseBody.trim().startsWith("{")) {
                            onError("Invalid server response")

                            return
                        }

                        if (!responseBody.trim().startsWith("{")) {
                            onError("Invalid payment response from server")
                            return
                        }

                        val json = JSONObject(responseBody)


                        val intent = json.getString("intent")
                        val confidence = json.getDouble("confidence").toFloat()

                        val itemsJson = json.getJSONArray("items")
                        val missing = mutableListOf<String>()
                        if (json.has("missing")) {
                            val missingJson = json.getJSONArray("missing")
                            for (i in 0 until missingJson.length()) {
                                missing.add(missingJson.getString(i))
                            }
                        }


                        val items = mutableListOf<CartItem>()

                        for (i in 0 until itemsJson.length()) {
                            val item = itemsJson.getJSONObject(i)

                            // 🔹 Parse alternatives if present
                            val alternatives =
                                if (item.has("alternatives")) {
                                    val altArray = item.getJSONArray("alternatives")
                                    (0 until altArray.length()).map { idx ->
                                        val alt = altArray.getJSONObject(idx)
                                        AltItem(
                                            name = alt.getString("name"),
                                            price = alt.getInt("price")
                                        )
                                    }
                                } else {
                                    emptyList()
                                }

                            items.add(
                                CartItem(
                                    name = item.getString("name"),
                                    quantity = item.getInt("quantity"),
                                    price = item.getInt("price"),
                                    recommended = item.optBoolean("recommended", false),
                                    reason = item.optString("reason", null),
                                    alternatives = alternatives
                                )
                            )
                        }

                        onResult(
                            AgentIntent(
                                intent = intent,
                                confidence = confidence,
                                items = items,
                                missing = missing
                            )
                        )

                    }
                })
            }

            fun fetchPaymentDecision(
                userId: String,
                items: List<CartItem>,
                onResult: (method: String, note: String, allowSwitch: Boolean, total: Int) -> Unit,
                onError: (String) -> Unit
            ) {
                val url = "http://172.20.10.3:3000/payment/decision"

                val jsonBody = JSONObject().apply {
                    put("userId", userId)

                    val itemsArray = org.json.JSONArray()

                    items.forEach {
                        val obj = JSONObject()
                        obj.put("name", it.name)
                        obj.put("quantity", it.quantity)
                        itemsArray.put(obj)
                    }

                    put("items", itemsArray)
                }

                val body = jsonBody.toString().toRequestBody(JSON)

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                client.newCall(request).enqueue(object : Callback {

                    override fun onFailure(call: Call, e: IOException) {
                        onError(e.message ?: "Network error")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()

                        if (responseBody == null) {
                            onError("Empty response")
                            return
                        }

                        try {
                            val json = JSONObject(responseBody)

                            val method = json.getString("recommendedMethod")
                            val note = json.getString("note")
                            val allowSwitch = json.getBoolean("allowSwitch")
                            val total = json.getInt("total")
                            onResult(method, note, allowSwitch, total)

                        } catch (e: Exception) {
                            onError("Invalid payment response")
                        }
                    }
                })
            }


            fun recalculateTotal(
                items: List<CartItem>,
                onResult: (Int) -> Unit,
                onError: (String) -> Unit
            ) {
                val url = "http://172.20.10.3:3000/cart/recalculate"

                val itemsArray = org.json.JSONArray()
                items.forEach {
                    val obj = JSONObject()
                    obj.put("name", it.name)
                    obj.put("quantity", it.quantity)
                    itemsArray.put(obj)
                }

                val body = JSONObject()
                    .put("items", itemsArray)
                    .toString()
                    .toRequestBody(JSON)

                val request = Request.Builder().url(url).post(body).build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        onError(e.message ?: "Failed to recalc")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val json = JSONObject(response.body?.string() ?: "{}")
                        onResult(json.getInt("total"))
                    }
                })
            }

            fun createOrder(
                userId: String,
                items: List<CartItem>,
                onResult: (orderId: String, total: Int) -> Unit,
                onError: (String) -> Unit
            ) {
                val url = "http://172.20.10.3:3000/order/create"

                val json = JSONObject().apply {
                    put("userId", userId)
                    val arr = org.json.JSONArray()
                    items.forEach {
                        val obj = JSONObject()
                        obj.put("name", it.name)
                        obj.put("quantity", it.quantity)
                        arr.put(obj)
                    }
                    put("items", arr)
                }

                val body = json.toString().toRequestBody(JSON)

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        onError(e.message ?: "Order create failed")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val res = response.body?.string() ?: return onError("Empty response")
                        val obj = JSONObject(res)
                        onResult(
                            obj.getString("orderId"),
                            obj.getInt("total")
                        )
                    }
                })
            }

            fun fetchUpiIntent(
                orderId: String,
                onResult: (upiUrl: String) -> Unit,
                onError: (String) -> Unit
            ) {
                val url = "http://172.20.10.3:3000/payment/upi/intent"

                val json = JSONObject().apply {
                    put("orderId", orderId)
                }

                val body = json.toString().toRequestBody(JSON)

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        onError(e.message ?: "UPI intent failed")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val res = response.body?.string() ?: return onError("Empty response")
                        val obj = JSONObject(res)
                        onResult(obj.getString("upiIntentUrl"))
                    }
                })
            }
            fun recordBrandEvent(
                userId: String,
                category: String,
                product: String,
                onError: (String) -> Unit = {}
            ) {
                val url = "http://172.20.10.3:3000/brand/record"

                val jsonBody = JSONObject().apply {
                    put("userId", userId)
                    put("category", category)
                    put("product", product)
                }

                val body = jsonBody.toString().toRequestBody(JSON)

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        onError(e.message ?: "Network error")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        // no UI response needed
                        response.close()
                    }
                })
            }
            fun recordPaymentEvent(
                userId: String,
                method: String,
                amount: Int,
                status: String
            ) {
                val url = "http://172.20.10.3:3000/payment/record"

                val jsonBody = JSONObject().apply {
                    put("userId", userId)
                    put("method", method)
                    put("amount", amount)
                    put("status", status)
                }

                val body = jsonBody.toString().toRequestBody(JSON)

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("@@PaymentRecord", "Failed: ${e.message}")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        Log.d("@@PaymentRecord", "Recorded payment event")
                    }
                })
            }
        }
        fun getUnsafeOkHttpClient(): OkHttpClient {
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            )

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())

            val sslSocketFactory = sslContext.socketFactory

            return OkHttpClient.Builder()
                .sslSocketFactory(
                    sslSocketFactory,
                    trustAllCerts[0] as X509TrustManager
                )
                .hostnameVerifier { _, _ -> true }
                .build()
        }
