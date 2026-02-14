import com.example.aiagentdemo.Product
import com.example.aiagentdemo.R

val products = listOf(

    Product(
        name = "Egg",
        price = 6,
        imageRes = R.drawable.egg,
        category = "egg"
    ),

    // Bread – multiple variants
    Product(
        name = "Bread White",
        price = 40,
        imageRes = R.drawable.white_bread,
        category = "bread"
    ),

    Product(
        name = "Bread Brown",
        price = 45,
        imageRes = R.drawable.brown_bread,
        category = "bread"
    ),

    Product(
        name = "Bread Multigrain",
        price = 50,
        imageRes = R.drawable.multigrain_bread,
        category = "bread"
    ),


    Product(
        name = "Bread Oats",
        price = 52,
        imageRes = R.drawable.oats_bread,
        category = "bread"
    ),

    // Milk – multiple brands
    Product(
        name = "Milk Amul",
        price = 30,
        imageRes = R.drawable.milk,

        category = "milk"
    ),

    Product(
        name = "Milk Mother Dairy",
        price = 32,
        imageRes = R.drawable.motherdairy,
        category = "milk"
    ),

    Product(
        name = "Milk Nandini",
        price = 35,
        imageRes = R.drawable.nandinimilk,
        category = "milk"
    ),

    // Curd – multiple brands
    Product(
        name = "Curd Amul",
        price = 45,
        imageRes = R.drawable.curd_amul,
        category = "curd"
    ),

    Product(
        name = "Curd hatsun",
        price = 42,
        imageRes = R.drawable.curd_hatsun,
        category = "curd"
    ),

    Product(
        name = "Curd heritage",
        price = 40,
        imageRes = R.drawable.curd_heritage,
        category = "curd"
    ),

    // Grocery
    Product(
        name = "Rice 5kg",
        price = 260,
        imageRes = R.drawable.rice,
        category = "rice"
    ),

    Product(
        name = "Cooking Oil 1L",
        price = 180,
        imageRes = R.drawable.oil,
        category = "oil"
    ),

    Product(
        name = "Sugar 2kg",
        price = 90,
        imageRes = R.drawable.sugar,
        category = "sugar"
    ),

    // High-value item
    Product(
        name = "Mixer Grinder",
        price = 1299,
        imageRes = R.drawable.mixer,
        category = "mixer"
    ),

    Product(
        name = "iPhone 17",
        price = 129999,
        imageRes = R.drawable.apple_17,
        category = "iphone"
    ),

    Product(
        name = "Power Bank",
        price = 2499,
        imageRes = R.drawable.powerbank,
        category = "power bank"
    ),
)
