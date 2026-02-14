# Agentic AI Grocery Application 🛒🤖

The **Agentic AI Grocery Application** is a demo implementation of an **agentic AI–powered grocery ordering system** designed to work as a plugin or standalone intelligent interface. The core goal of this application is to **minimize human intervention** by allowing AI agents to autonomously understand user intent, take decisions, and complete the entire ordering workflow end-to-end.

The system combines a **Node.js backend**, an **Android frontend**, and an **LLM (LLaMA 3)** to create a hands-free, intent-driven grocery shopping experience.

---

## 🎯 Core Objective

To demonstrate how **Agentic AI** can:

* Understand natural user intent (text or voice)
* Make autonomous decisions
* Execute multi-step actions
* Reduce manual UI interactions

This project showcases how AI agents can replace traditional form-based and click-heavy shopping flows.

---

## 🔍 Problem Statement

Conventional grocery apps:

* Require excessive manual input
* Depend heavily on UI navigation
* Offer limited personalization
* Interrupt user flow during checkout and payment

This leads to:

* Higher friction
* Slower ordering
* Poor accessibility

---

## 💡 Solution Overview

The Agentic AI Grocery App introduces an **AI-driven ordering agent** that manages the complete lifecycle:

* Product understanding
* Cart management
* Recommendation
* Payment decisioning
* Order confirmation

All with **minimal human interaction**.

---

## 🧠 Agentic AI Workflow

### 🗣️ Input Handling

* Users can place orders using:

  * **Text input**
  * **Audio (voice) input**
* Example:

  > "Order 2 packets of milk, bread, and fruits"

The **LLaMA 3 LLM** processes:

* User intent
* Product names
* Quantities
* Implicit preferences

---

### 🛒 Intelligent Cart Automation

* The AI agent:

  * Automatically adds items to the cart
  * Extracts quantities without manual selection
* If a category contains multiple products:

  * The system queries the **user preference database**
  * Recommends the **most frequently ordered product**

Users can:

* Accept the recommendation
* Switch to alternative products if desired

---

### ⭐ Personalized Recommendations

* Recommendations are based on:

  * Historical user purchases
  * Category-level frequency
* This balances:

  * Automation
  * User control

---

### 💳 Intelligent Payment Recommendation

On the same screen:

* Multiple payment options are displayed:

  * UPI
  * Card

The AI agent recommends a payment method based on:

* Order amount
* Previously used payment method for similar amounts
* User payment behavior patterns

---

### 🔐 Secure Checkout & Verification

* On clicking **Pay**:

  * OTP verification is triggered automatically
  * No manual redirection required
* After successful verification:

  * An **Order Summary page** is displayed
  * Final order details are confirmed

---

## 🧩 End-to-End Flow Summary

1. User gives text or voice command
2. LLM understands intent and quantities
3. AI agent builds cart automatically
4. Preferred products are recommended
5. Payment method is intelligently suggested
6. OTP verification completes checkout
7. Order summary is shown

👉 All within a **single, seamless flow**.

---

## 🏗️ Project Structure

```
agentic-ai-app/
├── backend/          # Node.js backend (Agent logic & APIs)
├── android-app/      # Android frontend
├── screenshots/      # Application screenshots
├── README.md
└── .gitignore
```

---

## ⚙️ Tech Stack

### Backend

* Node.js
* Express.js
* LLM Integration (LLaMA 3)
* Agent orchestration logic

### Frontend

* Android (Kotlin)
* Voice & Text input handling

### AI & Intelligence

* LLaMA 3 (Intent & entity extraction)
* Preference-based recommendation engine
* Rule + behavior-driven decision logic

---

## ▶️ Running the Backend Locally

```bash
cd backend
node server.js
```

> Update the backend IP address in the Android app before running.

---

## 🔐 Key Design Principles

* Minimal human intervention
* Agent-driven decision making
* Personalized user experience
* Seamless checkout flow
* Plugin-ready architecture

---

## 🚀 Key Highlights

* Voice + text based ordering
* Autonomous cart creation
* Personalized product recommendation
* Smart payment gateway suggestion
* OTP-based secure checkout
* End-to-end agentic workflow

---

## 📌 Future Enhancements

* Multi-agent coordination (inventory, delivery, payments)
* Real-time stock validation
* Cloud deployment
* Multi-language voice support
* Analytics dashboard

---

## 👨‍💻 Author

**Om Pisal**
Final Year Engineering Student (AI & Data Science)

---

## 📸 Screenshots

Screenshots of the Agentic AI Grocery application are available in the `screenshots/` folder.
