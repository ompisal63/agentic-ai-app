package com.example.aiagentdemo

sealed class AgentState(val label: String) {

    object Idle : AgentState("Idle")

    object Processing : AgentState("AI is adding your order to cart…")

    object IntentDetected : AgentState("Intent Detected")

    object CartPreview : AgentState("Cart Preview")

    /* ---------------- PAYMENT FLOW ---------------- */

    object PaymentSelection : AgentState("AutoPaymentRouting")

    object PaymentConfirmUPI : AgentState("Confirm UPI Payment")

    object PaymentConfirmCard : AgentState("Confirm Card Payment")

    object ConfirmingPayment : AgentState("Verifying payment details")

    object OtpVerification : AgentState("OTP Verification")

    object PaymentProcessing : AgentState("Processing Payment…")

    object PaymentSuccess : AgentState("Payment Successful")

    object OrderComplete : AgentState("Order Complete")

    object AutoPaymentRouting : AgentState("Auto selecting payment method")

}
