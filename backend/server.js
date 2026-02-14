      import express from "express";
        import cors from "cors";
        import axios from "axios";
        import path from "path";
        import { readJson, writeJson } from "./utils/fileStore.js";

        const AVAILABLE_PAYMENT_METHODS = ["UPI", "CARD"];
        const MERCHANT_UPI_ID = "hitachistore@upi"; // demo UPI ID
        const MERCHANT_NAME = "Hitachi Store";


        const PAYMENT_FILE = path.join(
          process.cwd(),
          "data",
          "paymentEvents.json"
        );

        const BRAND_FILE = path.join(
          process.cwd(),
          "data",
          "brandEvents.json"
        );

        const ORDER_FILE = path.join(
          process.cwd(),
          "data",
          "orders.json"
        );

        const app = express();
        app.use(cors());
        app.use(express.json());

        const OLLAMA_URL = "http://localhost:11434/api/generate";

        /* --------------------------------------------------
        OTP STORE (IN-MEMORY)
      -------------------------------------------------- */
      const OTP_STORE = new Map();
      // userId -> { otp, expiresAt }


        /* --------------------------------------------------
          PRODUCT CATALOG 
        -------------------------------------------------- */
        const PRODUCT_CATALOG = [
          {
            name: "Egg",
            category: "egg",
            aliases: ["egg", "eggs"],
            price: 6
          },

          // Bread variants
          {
            name: "Bread White",
            category: "bread",
            aliases: ["bread", "white bread"],
            price: 40
          },
          {
            name: "Bread Brown",
            category: "bread",
            aliases: ["bread", "brown bread"],
            price: 45
          },
          {
            name: "Bread Multigrain",
            category: "bread",
            aliases: ["bread", "multigrain bread"],
            price: 50
          },
          {
            name: "Bread Oats",
            category: "bread",
            aliases: ["bread", "oats bread"],
            price: 52
          },

          // Milk variants
          {
            name: "Milk Amul",
            category: "milk",
            aliases: ["milk", "amul doodh"],
            price: 30
          },
          {
            name: "Milk Mother Dairy",
            category: "milk",
            aliases: ["milk", "mother dairy milk", "mother dairy doodh"],
            price: 32
          },
          {
            name: "Milk Nandini",
            category: "milk",
            aliases: ["milk", "nandini milk", " nandini doodh"],
            price: 35
          },

          // Curd variants
          {
            name: "Curd Amul",
            category: "curd",
            aliases: ["curd", "dahi", "amul dahi"],
            price: 45
          },
          {
            name: "Curd Hatsun",
            category: "curd",
            aliases: ["curd", "hatsun curd", "hatsun dahi"],
            price: 42
          },
          {
            name: "Curd Heritage",
            category: "curd",
            aliases: ["curd", "heritage curd", "heritage dahi"],
            price: 40
          },

          // Grocery
          {
            name: "Rice 5kg",
            category: "rice",
            aliases: ["rice", "chawal", "bhat", "bhaat"],
            price: 260
          },
          {
            name: "Cooking Oil 1L",
            category: "oil",
            aliases: ["oil", "cooking oil", "Tel"],
            price: 180
          },
          {
            name: "Sugar 2kg",
            category: "sugar",
            aliases: ["sugar", "cheeni", "saakhar", "sakhar"],
            price: 90
          },

          // Appliance
          {
            name: "Mixer Grinder",
            category: "mixer",
            aliases: ["mixer", "grinder", "mixer grinder", "mixie"],
            price: 1299,
            single: true
          },
          {
            name: "iPhone 17",
            category: "iphone",
            aliases: ["iphone", "iphone 17", "apple phone"],
            price: 129999,
            single: true
          },
          {
          name: "Power Bank",
          category: "powerbank",
          aliases: ["power bank", "powerbank", "battery bank"],
          price: 2499,
          single: true
          }
        ];

        /* --------------------------------------------------
          HEALTH CHECK
        -------------------------------------------------- */
        app.get("/status", (req, res) => {
          res.json({ status: "Backend running (Ollama AI)" });
        });


        /* --------------------------------------------------
        PRODUCT CATALOG API
      -------------------------------------------------- */
        app.get("/catalog", (req, res) => {
          res.json({
          products: PRODUCT_CATALOG
          });
        });


        /* --------------------------------------------------
          CHAT → OLLAMA
        -------------------------------------------------- */
        app.post("/chat", async (req, res) => {
          const text = req.body?.text;


          if (!text) {
            return res.status(400).json({ error: "Text is required" });
          }

          try {
            /* ---------- PROMPT ---------- */
            const prompt = `
            You are an AI assistant for a grocery delivery app.

            CRITICAL RULES:
            - Respond with VALID JSON ONLY
            - NO comments
            - NO explanations
            - NO trailing text
            - NO markdown
            - NO // or /* */ anywhere

            TASK:
            - Identify product concepts from user input
            - Include concepts even if product may not exist
            - Extract quantity if mentioned, else default to 1

            RESPONSE FORMAT (STRICT JSON):
            {
              "intent": "ADD_ITEMS",
              "concepts": [
                { "name": "iphone", "quantity": 1 }
              ]
            }

            User input:
            "${text}"
            `;


        const ollamaResponse = await axios.post(
          OLLAMA_URL,
          {
            model: "llama3:latest",
            prompt,
            stream: false,
            options: {
              temperature: 0,
              num_predict: 200
            }
          },
          {
            timeout: 0
          }
        );

          const aiText = ollamaResponse.data.response;

        let aiResult;
        try {
          // 🔴 Extract only the JSON part from LLM response
          const jsonMatch = aiText.match(/\{[\s\S]*\}/);
          if (!jsonMatch) throw new Error("No JSON found");

          const cleaned = jsonMatch[0].replace(/\/\/.*$/gm, "").trim();
          aiResult = JSON.parse(cleaned);

        } catch (e) {
          console.error("LLM PARSE FAILED:", aiText);
          return res.json({
            intent: "UNKNOWN",
            confidence: 0.2,
            items: []
          });
        }

        const items = [];
        const missing = [];


        const concepts = aiResult.concepts || [];

        concepts.forEach(c => {
          const conceptName = c.name.toLowerCase();
          // ✅ Quantity fix: only allow >1 if user explicitly said a number
        const explicitQuantityMentioned =
          /\b(one|two|three|four|five|six|seven|eight|nine|\d+)\b/i.test(text);

        const quantity =
          explicitQuantityMentioned && typeof c.quantity === "number" && c.quantity > 0
            ? c.quantity
            : 1;


          const matchingProducts = PRODUCT_CATALOG.filter(p =>
            p.aliases.some(alias => {
              const a = alias.toLowerCase();
              return conceptName.includes(a) || a.includes(conceptName);
            })
          );


      const product = matchingProducts.length > 0
        ? matchingProducts[0]
        : null;


        
        if (product) {
          if (!items.some(i => i.name === product.name)) {

            const category = product.category;
            const categoryProducts = PRODUCT_CATALOG.filter(
        p => p.category === category
      );
      const hasMultipleOptions =
        !product.single && categoryProducts.length > 1;



            // 1️⃣ Read brand events (real-time)
        const BRAND_FILE = path.join(
          process.cwd(),
          "data",
          "brandEvents.json"
        );

        const brandEvents = readJson(BRAND_FILE).filter(
          e => e.userId === "demo-user" && e.category === category
        );

        let finalProduct = product;
      let recommended = false;
      let reason = null;

      if (hasMultipleOptions && brandEvents.length > 0) {
        const usageCount = brandEvents.reduce((acc, e) => {
          acc[e.product] = (acc[e.product] || 0) + 1;
          return acc;
        }, {});

        const mostOrderedProductName =
          Object.entries(usageCount)
            .sort((a, b) => b[1] - a[1])[0][0];

        const historyProduct = categoryProducts.find(
          p => p.name === mostOrderedProductName
        );

        if (historyProduct) {
          finalProduct = historyProduct;
          recommended = true;
          reason = "Based on your past orders";
        }
      }


          // 3️⃣ Find alternatives (same category, other brands)

      const alternatives = hasMultipleOptions
        ? categoryProducts.filter(p => p.name !== finalProduct.name)
        : [];

      items.push({
        name: finalProduct.name,
        quantity,
        price: finalProduct.price,
        category,
        recommended,
        reason,
        alternatives: alternatives.map(a => ({
          name: a.name,
          price: a.price
        }))
      });


          }
        }
        else {
          missing.push(c.name);

        }
        });

        return res.json({
          intent: items.length > 0 ? "ORDER" : "UNKNOWN",
          confidence: items.length > 0 ? 0.9 : 0.2,
          items,
          missing
        });

          } catch (err) {
            console.error("CHAT ERROR:", err);
            return res.status(500).json({ error: "Processing failed" });
          }
        });


        /* --------------------------------------------------
          BRAND DECISION (REAL-TIME READ)
        -------------------------------------------------- */
        app.post("/brand/decision", (req, res) => {
          const { userId, category } = req.body;

          if (!userId || !category) {
            return res.status(400).json({
              error: "userId and category required"
            });
          }

          try {
            const BRAND_FILE = path.join(
              process.cwd(),
              "data",
              "brandEvents.json"
            );

            const events = readJson(BRAND_FILE).filter(
              e => e.userId === userId && e.category === category
            );

            // 🟡 No history yet → no recommendation
            if (events.length === 0) {
              return res.json({
                recommendedProduct: null
              });
            }

            // 🔢 Count frequency
            const usageCount = events.reduce((acc, e) => {
              acc[e.product] = (acc[e.product] || 0) + 1;
              return acc;
            }, {});

            const recommendedProduct =
              Object.entries(usageCount)
                .sort((a, b) => b[1] - a[1])[0][0];

            res.json({
              recommendedProduct,
              reason: "Based on your past orders"
            });

          } catch (err) {
            console.error("BRAND DECISION ERROR:", err);
            res.status(500).json({ error: "Failed to decide brand" });
          }
        });


        app.post("/brand/record", async (req, res) => {
          let { userId, category, product } = req.body;

          if (!userId || !category || !product) {
            return res.status(400).json({
              error: "userId, category, product required"
            });
          }

          // 🔒 HARD NORMALIZATION (NO ESCAPE)
          userId = String(userId).toLowerCase().trim();
          category = String(category).toLowerCase().trim();
          product = String(product).trim();

          const event = {
            userId,
            category,
            product,
            event: "ORDERED",
            timestamp: Date.now()
          };

          try {
            const events = readJson("data/brandEvents.json");
            events.unshift(event);
            writeJson("data/brandEvents.json", events);

            res.json({ ok: true });
          } catch (err) {
            console.error("BRAND RECORD ERROR:", err);
            res.status(500).json({ error: "Failed to record brand event" });
          }
        });


        /* --------------------------------------------------
          PAYMENT DECISION 
        -------------------------------------------------- */
        app.post("/payment/decision", (req, res) => {

          const { userId, items } = req.body;

        if (!Array.isArray(items) || items.length === 0) {
          return res.status(400).json({ error: "Items required" });
        }

        const total = calculateTotalFromItems(items);

          const normalizedUserId = String(userId || "").toLowerCase().trim();
          if (!normalizedUserId) {
            return res.status(400).json({ error: "userId required" });
          }

        const allTransactions = readJson(PAYMENT_FILE)
          .filter(txn => txn.userId === normalizedUserId)
          .sort((a, b) => b.timestamp - a.timestamp);

        const last10Transactions =
          allTransactions.length >= 10
            ? allTransactions.slice(0, 10)
            : allTransactions;


          let recommendedMethod = null;
          let note = null;

          

          // =========================
          // CONDITION 
          // =========================

      // RULE 1: Low amount → prefer UPI (fast + low failure rate)
          if (total < 1000) {
            recommendedMethod = "UPI";
            note = "⚡ Since your total is below ₹1000, we’ve selected below methods for you!";
          } else {

        // Step 2: only successful txns above ₹1000
        const eligibleTxns = last10Transactions.filter(
          t => t.status === "SUCCESS" && t.amount > total
        );

        // Step 3: count usage per method
        const usageCount = eligibleTxns.reduce((acc, txn) => {
          acc[txn.method] = (acc[txn.method] || 0) + 1;
          return acc;
        }, {});


            const historyChoice =
              Object.entries(usageCount).sort((a, b) => b[1] - a[1])[0]?.[0];

            recommendedMethod = historyChoice && AVAILABLE_PAYMENT_METHODS.includes(historyChoice)
              ? historyChoice
              : "CARD";
            note = `⚡ Based on your recent successful payments, we’ve selected ${recommendedMethod}`;
          }

          // =========================
          // CONDITION 1: FAILURE AWARE
          // =========================
          const recentlyFailed = last10Transactions
            .slice(0, 3)
            .filter(t => t.status === "FAILED")
            .map(t => t.method);

      // RULE 3: Recent failures override preference (risk mitigation)
          if (recentlyFailed.includes(recommendedMethod)) {
            recommendedMethod = recommendedMethod === "UPI" ? "CARD" : "UPI";
            note = "⚠️ We switched your payment method due to a recent failure";
          }


          return res.json({
            recommendedMethod,
            note,
            allowSwitch: true,
            availableMethods: AVAILABLE_PAYMENT_METHODS,
            total
          });

        });



        function calculateTotalFromItems(items) {
        let total = 0;

        items.forEach(item => {
          const product = PRODUCT_CATALOG.find(
            p => p.name.toLowerCase() === item.name.toLowerCase()
          );
          if (product) {
            total += product.price * (item.quantity || 1);
          }
        });

        return total;
        }

        app.post("/cart/recalculate", (req, res) => {
        const { items } = req.body;

        if (!Array.isArray(items)) {
          return res.status(400).json({ error: "Items required" });
        }

        const total = calculateTotalFromItems(items);
        res.json({ total });
        });

        /* --------------------------------------------------
        ORDER CREATE (LOCK AMOUNT BEFORE PAYMENT)
         -------------------------------------------------- */
      app.post("/order/create", (req, res) => {
        const { userId, items } = req.body;

        if (!userId || !Array.isArray(items) || items.length === 0) {
          return res.status(400).json({ error: "userId and items required" });
        }

        const total = calculateTotalFromItems(items);

        const order = {
          orderId: `ORD-${Date.now()}`,
          userId: String(userId).toLowerCase().trim(),
          items,
          total,
          status: "PAYMENT_PENDING",
          createdAt: Date.now()
        };

        const orders = readJson(ORDER_FILE);
        orders.unshift(order);
        writeJson(ORDER_FILE, orders);

        res.json({
          orderId: order.orderId,
          total: order.total,
          status: order.status
        });
      });
     
        /* --------------------------------------------------
        UPI INTENT GENERATION (GPay / PhonePe)
        -------------------------------------------------- */
      app.post("/payment/upi/intent", (req, res) => {
        const { orderId } = req.body;

        if (!orderId) {
        return res.status(400).json({ error: "orderId required" });
        }

        const orders = readJson(ORDER_FILE);
        const order = orders.find(o => o.orderId === orderId);

        if (!order) {
          return res.status(404).json({ error: "Order not found" });
        }

        if (order.status !== "PAYMENT_PENDING") {
          return res.status(400).json({
            error: `Order not eligible for payment (status=${order.status})`
          });
        }

      const upiUrl =
        `upi://pay` +
        `?pa=${encodeURIComponent(MERCHANT_UPI_ID)}` +
        `&pn=${encodeURIComponent(MERCHANT_NAME)}` +
        `&am=${encodeURIComponent(order.total)}` +
        `&cu=INR` +
        `&tn=${encodeURIComponent(`Order ${order.orderId}`)}`;

      res.json({
        orderId: order.orderId,
        method: "UPI",
        preferredApp: "GPAY",
        upiIntentUrl: upiUrl,
        amount: order.total
      });
    });

        app.post("/payment/record", (req, res) => {
          const { userId, method, amount, status, items } = req.body;

          if (!userId || !method || !amount || !status) {
            return res.status(400).json({ error: "Invalid payload" });
          }

        const normalizedMethod = String(method).toUpperCase().trim();

      // 🔒 HARD PAYMENT METHOD VALIDATION
        if (!AVAILABLE_PAYMENT_METHODS.includes(normalizedMethod)) {
          return res.status(400).json({
            error: "Invalid payment method"
          });
        }


          // ---------------- PAYMENT EVENT ----------------
          const paymentEvents = readJson(PAYMENT_FILE);

          paymentEvents.unshift({
            userId,
            method: normalizedMethod,
            amount,
            status,
            timestamp: Date.now()
          });


          writeJson(PAYMENT_FILE, paymentEvents);

          // ---------------- BRAND LEARNING ----------------
          if (status === "SUCCESS" && Array.isArray(items)) {
            const brandEvents = readJson(BRAND_FILE);

            items.forEach(item => {
              brandEvents.push({
                userId,
                category: item.category,
                product: item.name,
                event: "ORDERED",
                timestamp: Date.now()
              });
            });

            writeJson(BRAND_FILE, brandEvents);
          }

          res.json({ ok: true });
        });


        const PORT = 3000;
        async function warmupOllama() {
          try {
            await axios.post(
              OLLAMA_URL,
              {
                model: "llama3:latest",
                prompt: "Hello",
                stream: false
              },
              { timeout: 30000 }
            );
            console.log(" Ollama warmed up");
          } catch (e) {
            console.log(" Ollama warmup failed ");
          }
        }

function keepOllamaAlive() {
  setInterval(async () => {
    try {
      await axios.post(
        OLLAMA_URL,
        {
          model: "llama3:latest",
          prompt: "ping",
          stream: false,
          options: {
            num_predict: 1
          }
        },
        { timeout: 0 }
      );

      console.log(" Ollama heartbeat OK");
    } catch (e) {
      console.log(" Ollama heartbeat failed");
    }
  }, 30000); // every 30 seconds
}

        /* -------------------------------------------------- */
        app.listen(PORT, async () => {
          console.log(`Server running on port ${PORT}`);
          await warmupOllama();
          keepOllamaAlive();
        });
