package com.example.data.repository

data class CategoryDefinition(
    val id: String,
    val name: String,
    val normalizedName: String,
    val subcategories: List<SubcategoryDefinition>
)

data class SubcategoryDefinition(
    val id: String,
    val name: String,
    val normalizedName: String,
    val aliases: List<String>,
    val compatibleTypes: List<String> = listOf("EXPENSE")
)

object TaxonomyData {
    const val VERSION = 2

    val CATEGORIES: List<CategoryDefinition> = listOf(
        // 1. Food & Drinks
        CategoryDefinition(
            id = "cat_food_drinks",
            name = "Food & Drinks",
            normalizedName = "food & drinks",
            subcategories = listOf(
                SubcategoryDefinition("sub_nashta", "Breakfast / Nashta", "breakfast / nashta", listOf("nashta", "breakfast", "halwa puri", "paratha roll", "anda paratha")),
                SubcategoryDefinition("sub_lunch", "Lunch", "lunch", listOf("lunch", "dopahar ka khana")),
                SubcategoryDefinition("sub_dinner", "Dinner", "dinner", listOf("dinner", "raat ka khana")),
                SubcategoryDefinition("sub_general_meals", "General Meals", "general meals", listOf("khana", "meal", "food", "restaurant", "hotel ka khana")),
                SubcategoryDefinition("sub_biryani", "Biryani / Pulao", "biryani / pulao", listOf("biryani", "pulao", "yakhni pulao")),
                SubcategoryDefinition("sub_karahi", "Karahi / Handi", "karahi / handi", listOf("karahi", "handi", "mutton karahi", "chicken karahi", "shinwari")),
                SubcategoryDefinition("sub_daal", "Daal / Salan", "daal / salan", listOf("daal", "salan", "daal mash", "daal chawal", "sabzi salan")),
                SubcategoryDefinition("sub_roti", "Roti / Naan / Paratha", "roti / naan / paratha", listOf("roti", "naan", "paratha", "kulcha", "chapati")),
                SubcategoryDefinition("sub_bbq", "BBQ / Tikka / Kebab", "bbq / tikka / kebab", listOf("bbq", "tikka", "kebab", "kabab", "boti", "seekh kebab", "chappal kabab")),
                SubcategoryDefinition("sub_burgers", "Burgers", "burgers", listOf("burger", "burgers", "zinger")),
                SubcategoryDefinition("sub_pizza", "Pizza", "pizza", listOf("pizza", "pizzas")),
                SubcategoryDefinition("sub_shawarma", "Shawarma / Rolls", "shawarma / rolls", listOf("shawarma", "shwarma", "roll paratha")),
                SubcategoryDefinition("sub_sandwiches", "Sandwiches", "sandwiches", listOf("sandwich", "sandwiches", "club sandwich")),
                SubcategoryDefinition("sub_fries", "Fries", "fries", listOf("fries", "french fries", "finger chips")),
                SubcategoryDefinition("sub_fried_chicken", "Fried Chicken", "fried chicken", listOf("fried chicken", "broast")),
                SubcategoryDefinition("sub_seafood", "Seafood", "seafood", listOf("fish", "seafood", "machli")),
                SubcategoryDefinition("sub_eggs", "Eggs", "eggs", listOf("egg", "eggs", "anday", "omlette", "omelette")),
                SubcategoryDefinition("sub_street_food", "Street Food", "street food", listOf("street food", "dhabba", "gol gappay", "chaat", "dahi bhallay")),
                SubcategoryDefinition("sub_samosa", "Samosa / Pakora", "samosa / pakora", listOf("samosa", "pakora", "pakoray", "samosay", "roll")),
                SubcategoryDefinition("sub_chips", "Chips / Crisps", "chips / crisps", listOf("chips", "crisps", "lays", "kurkure", "slims")),
                SubcategoryDefinition("sub_biscuits", "Biscuits", "biscuits", listOf("biscuit", "biscuits", "cookies")),
                SubcategoryDefinition("sub_nimco", "Nimco / Nuts", "nimco / nuts", listOf("nimco", "nuts", "badam", "pista", "mungphali", "peanuts")),
                SubcategoryDefinition("sub_fruit_food", "Fruit", "fruit", listOf("fruit", "fruits", "kela", "seb", "aam")),
                SubcategoryDefinition("sub_dry_fruit", "Dry Fruit", "dry fruit", listOf("dry fruit", "dry fruits", "chilgoza", "kaju")),
                SubcategoryDefinition("sub_sweets", "Sweets / Mithai", "sweets / mithai", listOf("mithai", "sweets", "gulab jamun", "jalebi", "halwa")),
                SubcategoryDefinition("sub_chocolate", "Chocolate / Candy", "chocolate / candy", listOf("chocolate", "candy", "toffee")),
                SubcategoryDefinition("sub_cakes", "Cakes / Bakery", "cakes / bakery", listOf("cake", "bakery", "pastry", "rusk", "patties")),
                SubcategoryDefinition("sub_ice_cream", "Ice Cream / Desserts", "ice cream / desserts", listOf("ice cream", "kulfi", "dessert")),
                SubcategoryDefinition("sub_tea", "Tea / Chai", "tea / chai", listOf("chai", "tea", "doodh patti", "karak chai", "kashmiri chai")),
                SubcategoryDefinition("sub_coffee", "Coffee", "coffee", listOf("coffee", "cappuccino", "espresso", "latte")),
                SubcategoryDefinition("sub_qehwa", "Qehwa", "qehwa", listOf("qehwa", "green tea", "kahwa")),
                SubcategoryDefinition("sub_water", "Water", "water", listOf("water", "mineral water", "pani")),
                SubcategoryDefinition("sub_soft_drinks", "Soft Drinks", "soft drinks", listOf("soft drink", "cold drink", "coke", "pepsi", "sprite", "soda")),
                SubcategoryDefinition("sub_juice", "Juice", "juice", listOf("juice", "fresh juice", "shake")),
                SubcategoryDefinition("sub_energy_drinks", "Energy Drinks", "energy drinks", listOf("energy drink", "red bull", "sting")),
                SubcategoryDefinition("sub_milk_dairy", "Milk / Dairy", "milk / dairy", listOf("milk", "doodh", "lassi", "yogurt", "dahi")),
                SubcategoryDefinition("sub_food_service", "Restaurant Service Charges", "restaurant service charges", listOf("service charges", "tip", "waiter tip")),
                SubcategoryDefinition("sub_food_delivery", "Food Delivery Charges", "food delivery charges", listOf("foodpanda", "delivery charges", "food delivery"))
            )
        ),

        // 2. Groceries & Household Food
        CategoryDefinition(
            id = "cat_groceries",
            name = "Groceries & Household Food",
            normalizedName = "groceries & household food",
            subcategories = listOf(
                SubcategoryDefinition("sub_groc_veg", "Vegetables", "vegetables", listOf("sabzi", "vegetables", "tamatar", "pyaz", "aloo")),
                SubcategoryDefinition("sub_groc_fruit", "Fruit (Grocery)", "fruit (grocery)", listOf("fruits grocery", "khalis phal")),
                SubcategoryDefinition("sub_groc_meat", "Meat", "meat", listOf("gosht", "meat", "beef", "mutton")),
                SubcategoryDefinition("sub_groc_poultry", "Poultry", "poultry", listOf("chicken", "murghi")),
                SubcategoryDefinition("sub_groc_fish", "Fish", "fish", listOf("raw fish", "taza machli")),
                SubcategoryDefinition("sub_groc_eggs", "Eggs (Grocery)", "eggs (grocery)", listOf("dozen eggs", "ando ki peti")),
                SubcategoryDefinition("sub_groc_dairy", "Milk / Dairy (Grocery)", "milk / dairy (grocery)", listOf("doodh khula", "olpers", "milkpak")),
                SubcategoryDefinition("sub_groc_grains", "Rice / Flour", "rice / flour", listOf("chawal", "atta", "flour", "rice", "maida")),
                SubcategoryDefinition("sub_groc_pulses", "Pulses", "pulses", listOf("daal packet", "chana", "lobia")),
                SubcategoryDefinition("sub_groc_oil", "Cooking Oil / Ghee", "cooking oil / ghee", listOf("cooking oil", "ghee", "tail")),
                SubcategoryDefinition("sub_groc_spices", "Spices", "spices", listOf("masalay", "spices", "mirch", "haldi", "zeera")),
                SubcategoryDefinition("sub_groc_sugar", "Sugar / Salt", "sugar / salt", listOf("cheeni", "namak", "sugar", "salt")),
                SubcategoryDefinition("sub_groc_packaged", "Packaged Food", "packaged food", listOf("packaged food", "ketchup", "mayo", "pasta")),
                SubcategoryDefinition("sub_groc_frozen", "Frozen Food", "frozen food", listOf("frozen food", "nuggets", "kabab pack")),
                SubcategoryDefinition("sub_groc_water", "Household Drinking Water", "household drinking water", listOf("water can", "nestle water gallon", "pani ka can"))
            )
        ),

        // 3. Transport
        CategoryDefinition(
            id = "cat_transport",
            name = "Transport",
            normalizedName = "transport",
            subcategories = listOf(
                SubcategoryDefinition("sub_bike_fuel", "Motorcycle Fuel", "motorcycle fuel", listOf("petrol", "petroll", "bike petrol", "fuel", "fueling", "tanki")),
                SubcategoryDefinition("sub_car_fuel", "Car Fuel", "car fuel", listOf("car petrol", "gari ka petrol")),
                SubcategoryDefinition("sub_diesel", "Diesel", "diesel", listOf("diesel")),
                SubcategoryDefinition("sub_cng", "CNG", "cng", listOf("cng")),
                SubcategoryDefinition("sub_taxi", "Taxi / Ride-hailing", "taxi / ride-hailing", listOf("taxi", "cab", "indrive", "careem", "uber", "yango")),
                SubcategoryDefinition("sub_rickshaw", "Rickshaw", "rickshaw", listOf("rickshaw", "rikshaw", "auto")),
                SubcategoryDefinition("sub_bus", "Bus / Coach", "bus / coach", listOf("bus", "coach", "daewoo", "faisal movers", "natco")),
                SubcategoryDefinition("sub_metro", "Metro", "metro", listOf("metro", "speedo", "orange line")),
                SubcategoryDefinition("sub_train", "Train", "train", listOf("train", "railway", "railway ticket")),
                SubcategoryDefinition("sub_flights", "Flights", "flights", listOf("flight", "air ticket", "pia", "airblue")),
                SubcategoryDefinition("sub_mountain_transport", "Jeep / Local Mountain Transport", "jeep / local mountain transport", listOf("jeep", "4x4", "pajero", "local van", "hiace")),
                SubcategoryDefinition("sub_vehicle_rental", "Vehicle Rental", "vehicle rental", listOf("car rent", "rent a car", "bike rental")),
                SubcategoryDefinition("sub_bicycle_rental", "Bicycle Rental", "bicycle rental", listOf("cycle rent", "bicycle")),
                SubcategoryDefinition("sub_tolls", "Tolls / M-tag", "tolls / m-tag", listOf("toll", "toll plaza", "m-tag", "mtag", "motorway toll")),
                SubcategoryDefinition("sub_parking", "Parking", "parking", listOf("parking", "parking fee", "parking ticket")),
                SubcategoryDefinition("sub_booking_fees", "Transport Booking Fees", "transport booking fees", listOf("booking fee", "terminal fee")),
                SubcategoryDefinition("sub_airport_transfers", "Airport Transfers", "airport transfers", listOf("airport cab", "airport drop")),
                SubcategoryDefinition("sub_freight", "Freight / Courier Travel Gear", "freight / courier travel gear", listOf("bilty", "courier", "cargo", "tcs gear"))
            )
        ),

        // 4. Motorcycle & Vehicle
        CategoryDefinition(
            id = "cat_motorcycle",
            name = "Motorcycle & Vehicle",
            normalizedName = "motorcycle & vehicle",
            subcategories = listOf(
                SubcategoryDefinition("sub_routine_service", "Routine Service", "routine service", listOf("bike service", "tuning", "service")),
                SubcategoryDefinition("sub_engine_oil", "Engine Oil", "engine oil", listOf("engine oil", "mobil oil", "oil change", "havoline", "zic")),
                SubcategoryDefinition("sub_oil_filter", "Oil Filter", "oil filter", listOf("oil filter")),
                SubcategoryDefinition("sub_air_filter", "Air Filter", "air filter", listOf("air filter")),
                SubcategoryDefinition("sub_chain_lube", "Chain Lubrication", "chain lubrication", listOf("chain lube", "chain oiling")),
                SubcategoryDefinition("sub_chain_sprockets", "Chain / Sprockets", "chain / sprockets", listOf("chain", "sprocket", "chain set", "garari")),
                SubcategoryDefinition("sub_brakes", "Brakes", "brakes", listOf("brake", "brake leathers", "brake pads", "disc brake")),
                SubcategoryDefinition("sub_tyres", "Tyres", "tyres", listOf("tyre", "tire", "panther", "service tyre")),
                SubcategoryDefinition("sub_tubes", "Tubes", "tubes", listOf("tube", "tyre tube")),
                SubcategoryDefinition("sub_puncture", "Puncture Repairs", "puncture repairs", listOf("puncture", "puncher", "hawa bharwayi")),
                SubcategoryDefinition("sub_battery", "Battery", "battery", listOf("battery", "bike battery", "acid")),
                SubcategoryDefinition("sub_electrical", "Electrical Repairs", "electrical repairs", listOf("wiring", "fuse", "headlight repair", "horn")),
                SubcategoryDefinition("sub_engine_repairs", "Engine Repairs", "engine repairs", listOf("engine kaam", "piston", "valve", "clutch plate")),
                SubcategoryDefinition("sub_clutch_cables", "Clutch / Cables", "clutch / cables", listOf("clutch wire", "race wire", "clutch cable", "accelerator cable")),
                SubcategoryDefinition("sub_spark_plugs", "Spark Plugs", "spark plugs", listOf("plug", "spark plug")),
                SubcategoryDefinition("sub_welding", "Welding", "welding", listOf("welding", "silencer welding")),
                SubcategoryDefinition("sub_mechanic_charges", "Labour / Mechanic Charges", "labour / mechanic charges", listOf("mechanic", "ustad", "labour", "dasti")),
                SubcategoryDefinition("sub_washing", "Washing", "washing", listOf("bike wash", "car wash", "dhulwayi")),
                SubcategoryDefinition("sub_helmet", "Helmet", "helmet", listOf("helmet", "helmets", "studds", "ls2")),
                SubcategoryDefinition("sub_riding_gloves", "Riding Gloves", "riding gloves", listOf("gloves", "riding gloves", "dastanay")),
                SubcategoryDefinition("sub_riding_jacket", "Riding Jacket", "riding jacket", listOf("riding jacket", "mesh jacket")),
                SubcategoryDefinition("sub_riding_pants", "Riding Pants", "riding pants", listOf("riding pants", "riding trousers")),
                SubcategoryDefinition("sub_boots", "Boots", "boots", listOf("riding boots", "shoes for bike")),
                SubcategoryDefinition("sub_guards", "Knee / Elbow Guards", "knee / elbow guards", listOf("knee guards", "elbow guards", "armor")),
                SubcategoryDefinition("sub_rain_gear", "Rain Gear", "rain gear", listOf("raincoat", "rain suit", "barsati")),
                SubcategoryDefinition("sub_phone_mount", "Phone Mount", "phone mount", listOf("mobile holder", "phone mount", "handle mount")),
                SubcategoryDefinition("sub_saddle_bags", "Saddle / Tank Bags", "saddle / tank bags", listOf("saddle bag", "tank bag", "side bags")),
                SubcategoryDefinition("sub_panniers", "Panniers / Top Box", "panniers / top box", listOf("top box", "panniers", "diggi")),
                SubcategoryDefinition("sub_bungee", "Bungee / Tie-down Straps", "bungee / tie-down straps", listOf("bungee cords", "rassa", "strap", "tie down")),
                SubcategoryDefinition("sub_crash_guards", "Crash Guards", "crash guards", listOf("crash guard", "leg guard")),
                SubcategoryDefinition("sub_lights", "Lights", "lights", listOf("fog lights", "aux lights", "led lights", "indicator")),
                SubcategoryDefinition("sub_mirrors", "Mirrors", "mirrors", listOf("side mirrors", "sheeshey")),
                SubcategoryDefinition("sub_visor", "Visor", "visor", listOf("windshield", "visor", "windscreen")),
                SubcategoryDefinition("sub_seat", "Seat Accessories", "seat accessories", listOf("seat cover", "cushion")),
                SubcategoryDefinition("sub_tools", "Tools", "tools", listOf("pana", "chabi", "toolkit", "allen key")),
                SubcategoryDefinition("sub_spare_parts", "Spare Parts", "spare parts", listOf("spare parts", "accessories", "bike ki accessories")),
                SubcategoryDefinition("sub_vehicle_reg", "Registration / Tax / Insurance", "registration / tax / insurance", listOf("token tax", "bike registration", "insurance"))
            )
        ),

        // 5. Accommodation & Housing
        CategoryDefinition(
            id = "cat_accommodation",
            name = "Accommodation & Housing",
            normalizedName = "accommodation & housing",
            subcategories = listOf(
                SubcategoryDefinition("sub_hotels", "Hotels", "hotels", listOf("hotel", "hotel room", "kamra")),
                SubcategoryDefinition("sub_guesthouses", "Guesthouses", "guesthouses", listOf("guesthouse", "guest house", "resthouse")),
                SubcategoryDefinition("sub_hostels", "Hostels", "hostels", listOf("hostel", "dormitory", "bunk bed")),
                SubcategoryDefinition("sub_resorts", "Resorts", "resorts", listOf("resort", "cottage", "hut")),
                SubcategoryDefinition("sub_camping_fees", "Camping Fees", "camping fees", listOf("camping site", "camp ground", "camping charges")),
                SubcategoryDefinition("sub_tent_rental", "Tent Rental", "tent rental", listOf("rent tent", "tent rent", "kiraye ka tent")),
                SubcategoryDefinition("sub_room_rent", "Room Rent", "room rent", listOf("room rent", "kamray ka kiraya")),
                SubcategoryDefinition("sub_house_rent", "House Rent", "house rent", listOf("ghar ka kiraya", "house rent", "rent")),
                SubcategoryDefinition("sub_shared_rent", "Shared Rent", "shared rent", listOf("shared rent", "contri rent")),
                SubcategoryDefinition("sub_security_deposit", "Security Deposit", "security deposit", listOf("security deposit", "biyana")),
                SubcategoryDefinition("sub_advance_rent", "Advance Rent", "advance rent", listOf("advance rent", "peshgi")),
                SubcategoryDefinition("sub_lodging_utilities", "Utilities Included in Lodging", "utilities included in lodging", listOf("heater charges", "generator charges")),
                SubcategoryDefinition("sub_booking_charges", "Booking Charges", "booking charges", listOf("airbnb fee", "agoda fee", "booking.com")),
                SubcategoryDefinition("sub_cleaning_charges", "Cleaning Charges", "cleaning charges", listOf("cleaning fee", "safai fee")),
                SubcategoryDefinition("sub_extra_bedding", "Extra Bedding", "extra bedding", listOf("gadda", "extra bed", "kambal")),
                SubcategoryDefinition("sub_late_checkout", "Late Checkout", "late checkout", listOf("late checkout fee"))
            )
        ),

        // 6. Utilities
        CategoryDefinition(
            id = "cat_utilities",
            name = "Utilities",
            normalizedName = "utilities",
            subcategories = listOf(
                SubcategoryDefinition("sub_electricity", "Electricity", "electricity", listOf("bijli", "electricity bill", "wapda", "kelectric")),
                SubcategoryDefinition("sub_gas", "Gas", "gas", listOf("gas bill", "sui gas", "cylinder refill", "lpg")),
                SubcategoryDefinition("sub_util_water", "Water Bill", "water bill", listOf("water bill", "wasa")),
                SubcategoryDefinition("sub_internet", "Internet", "internet", listOf("wifi", "ptcl bill", "nayatel", "stormfiber")),
                SubcategoryDefinition("sub_broadband_inst", "Broadband Installation", "broadband installation", listOf("internet installation", "router")),
                SubcategoryDefinition("sub_postpaid", "Mobile Postpaid", "mobile postpaid", listOf("postpaid bill", "jazz postpaid")),
                SubcategoryDefinition("sub_util_arrears", "Utility Arrears", "utility arrears", listOf("bill arrears", "pichla bill")),
                SubcategoryDefinition("sub_util_fees", "Utility Service Fees", "utility service fees", listOf("meter fee", "line fee"))
            )
        ),

        // 7. Communication & Digital
        CategoryDefinition(
            id = "cat_communication",
            name = "Communication & Digital",
            normalizedName = "communication & digital",
            subcategories = listOf(
                SubcategoryDefinition("sub_easyload", "Mobile Top-up / Easyload", "mobile top-up / easyload", listOf("easyload", "topup", "load", "mobile recharge")),
                SubcategoryDefinition("sub_data_packages", "Data Packages", "data packages", listOf("internet package", "data bundle", "4g package")),
                SubcategoryDefinition("sub_call_sms", "Call / SMS Packages", "call / sms packages", listOf("call package", "sms package")),
                SubcategoryDefinition("sub_sim_purchase", "SIM Purchase", "sim purchase", listOf("new sim", "sim card", "jazz sim", "zong sim")),
                SubcategoryDefinition("sub_scom", "SCOM Services", "scom services", listOf("scom", "scom sim", "scom load", "scom internet")),
                SubcategoryDefinition("sub_internet_cafe", "Internet Café", "internet café", listOf("net cafe", "gaming zone")),
                SubcategoryDefinition("sub_printing", "Printing", "printing", listOf("print", "printing", "printout")),
                SubcategoryDefinition("sub_photocopies", "Photocopies", "photocopies", listOf("photocopy", "photostate", "copy")),
                SubcategoryDefinition("sub_scanning", "Scanning", "scanning", listOf("scan", "document scanning")),
                SubcategoryDefinition("sub_domains", "Domain Names", "domain names", listOf("domain", "godaddy", "namecheap")),
                SubcategoryDefinition("sub_hosting", "Web Hosting", "web hosting", listOf("hosting", "server", "vps")),
                SubcategoryDefinition("sub_cloud", "Cloud Services", "cloud services", listOf("aws", "google cloud", "azure", "digitalocean")),
                SubcategoryDefinition("sub_ai_api", "AI / API Usage", "ai / api usage", listOf("groq api", "openai api", "anthropic", "api usage")),
                SubcategoryDefinition("sub_software_subs", "Software Subscriptions", "software subscriptions", listOf("github", "jetbrains", "chatgpt plus")),
                SubcategoryDefinition("sub_streaming", "Streaming Subscriptions", "streaming subscriptions", listOf("netflix", "spotify", "youtube premium")),
                SubcategoryDefinition("sub_app_purchases", "App Purchases", "app purchases", listOf("play store", "in-app purchase")),
                SubcategoryDefinition("sub_charging_fees", "Device Charging Fees", "device charging fees", listOf("mobile charging shop", "battery charge"))
            )
        ),

        // 8. Personal Care & Hygiene
        CategoryDefinition(
            id = "cat_personal_care",
            name = "Personal Care & Hygiene",
            normalizedName = "personal care & hygiene",
            subcategories = listOf(
                SubcategoryDefinition("sub_soap", "Soap", "soap", listOf("soap", "sabun", "body wash")),
                SubcategoryDefinition("sub_shampoo", "Shampoo", "shampoo", listOf("shampoo", "conditioner")),
                SubcategoryDefinition("sub_toothpaste", "Toothpaste", "toothpaste", listOf("toothpaste", "colgate", "sensodyne")),
                SubcategoryDefinition("sub_toothbrush", "Toothbrush", "toothbrush", listOf("toothbrush", "brush")),
                SubcategoryDefinition("sub_facewash", "Facewash", "facewash", listOf("facewash", "face wash")),
                SubcategoryDefinition("sub_deodorant", "Deodorant / Body Spray", "deodorant / body spray", listOf("body spray", "body spary", "deodorant", "axe", "fogg")),
                SubcategoryDefinition("sub_perfume", "Perfume / Attar", "perfume / attar", listOf("perfume", "attar", "itr", "cologne")),
                SubcategoryDefinition("sub_sunscreen", "Sunscreen", "sunscreen", listOf("sunscreen", "sunblock")),
                SubcategoryDefinition("sub_lotion", "Lotion", "lotion", listOf("lotion", "cold cream", "moisturizer", "vaseline")),
                SubcategoryDefinition("sub_lip_balm", "Lip Balm", "lip balm", listOf("lip balm", "chapstick")),
                SubcategoryDefinition("sub_sanitizer", "Sanitizer", "sanitizer", listOf("hand sanitizer", "dettol")),
                SubcategoryDefinition("sub_wet_wipes", "Wet Wipes", "wet wipes", listOf("wipes", "tissue roll", "wet wipes")),
                SubcategoryDefinition("sub_tissues", "Tissues", "tissues", listOf("tissue", "tissue box")),
                SubcategoryDefinition("sub_razors", "Razors", "razors", listOf("razor", "blade", "gillette")),
                SubcategoryDefinition("sub_shaving", "Shaving Supplies", "shaving supplies", listOf("shaving foam", "aftershave")),
                SubcategoryDefinition("sub_haircuts", "Haircuts", "haircuts", listOf("haircut", "barber", "naai", "bal katwaye")),
                SubcategoryDefinition("sub_beard_grooming", "Beard Grooming", "beard grooming", listOf("khat", "shave", "dari")),
                SubcategoryDefinition("sub_salon", "Salon Services", "salon services", listOf("salon", "facial")),
                SubcategoryDefinition("sub_toiletries", "Toiletries & Hygiene", "toiletries & hygiene", listOf("toiletries", "hygiene products"))
            )
        ),

        // 9. Clothing & Shopping
        CategoryDefinition(
            id = "cat_clothing",
            name = "Clothing & Shopping",
            normalizedName = "clothing & shopping",
            subcategories = listOf(
                SubcategoryDefinition("sub_shirts", "Shirts", "shirts", listOf("shirt", "tshirt", "t-shirt", "kurta")),
                SubcategoryDefinition("sub_pants", "Pants", "pants", listOf("pant", "jeans", "trouser", "shalwar")),
                SubcategoryDefinition("sub_jackets", "Jackets", "jackets", listOf("jacket", "leather jacket", "windbreaker")),
                SubcategoryDefinition("sub_sweaters", "Sweaters", "sweaters", listOf("sweater", "hoodie", "cardigan")),
                SubcategoryDefinition("sub_thermal_wear", "Thermal Wear", "thermal wear", listOf("thermal", "inners", "warm inners", "garam kapray")),
                SubcategoryDefinition("sub_socks", "Socks", "socks", listOf("socks", "jurabay")),
                SubcategoryDefinition("sub_shoes", "Shoes", "shoes", listOf("shoes", "joggers", "sneakers")),
                SubcategoryDefinition("sub_slippers", "Slippers", "slippers", listOf("slippers", "chappal", "sandals")),
                SubcategoryDefinition("sub_caps", "Caps", "caps", listOf("cap", "beanie", "woolen cap", "topi")),
                SubcategoryDefinition("sub_cloth_gloves", "Gloves", "gloves", listOf("warm gloves", "woolen gloves")),
                SubcategoryDefinition("sub_cloth_rainwear", "Rainwear", "rainwear", listOf("poncho", "umbrella", "chatri")),
                SubcategoryDefinition("sub_tailoring", "Tailoring", "tailoring", listOf("darzi", "tailor", "silai")),
                SubcategoryDefinition("sub_clothing_repairs", "Clothing Repairs", "clothing repairs", listOf("rafoo", "repair clothes")),
                SubcategoryDefinition("sub_bags", "Bags", "bags", listOf("bag", "tote bag", "duffel")),
                SubcategoryDefinition("sub_wallets", "Wallets", "wallets", listOf("wallet", "batwa")),
                SubcategoryDefinition("sub_watches", "Watches", "watches", listOf("watch", "ghari")),
                SubcategoryDefinition("sub_sunglasses", "Sunglasses", "sunglasses", listOf("sunglasses", "shades", "dhoop ka chashma")),
                SubcategoryDefinition("sub_accessories_gen", "General Accessories", "general accessories", listOf("belt", "keychain")),
                SubcategoryDefinition("sub_souvenirs", "Souvenirs & Gifts", "souvenirs & gifts", listOf("souvenir", "tohfa", "gifts", "gift for family")),
                SubcategoryDefinition("sub_local_crafts", "Local Crafts", "local crafts", listOf("crafts", "shwal", "pashmina", "hunza cap"))
            )
        ),

        // 10. Health & Medical
        CategoryDefinition(
            id = "cat_medical",
            name = "Health & Medical",
            normalizedName = "health & medical",
            subcategories = listOf(
                SubcategoryDefinition("sub_medicines", "Medicines", "medicines", listOf("medicine", "dawa", "panadol", "paracetamol", "disprin", "brufen")),
                SubcategoryDefinition("sub_first_aid", "First Aid", "first aid", listOf("first aid", "pyodine", "bandage", "sunnyplast", "gauze")),
                SubcategoryDefinition("sub_doctor", "Doctor Consultation", "doctor consultation", listOf("doctor", "checkup", "physician")),
                SubcategoryDefinition("sub_hospital", "Hospital Charges", "hospital charges", listOf("hospital", "clinic", "emergency")),
                SubcategoryDefinition("sub_emergency_care", "Emergency Care", "emergency care", listOf("drip", "injection", "emergency care")),
                SubcategoryDefinition("sub_lab_tests", "Laboratory Tests", "laboratory tests", listOf("blood test", "lab test", "x-ray")),
                SubcategoryDefinition("sub_dental", "Dental Care", "dental care", listOf("dentist", "tooth extraction")),
                SubcategoryDefinition("sub_eye_care", "Eye Care", "eye care", listOf("eye drops", "eye doctor")),
                SubcategoryDefinition("sub_glasses", "Prescription Glasses", "prescription glasses", listOf("nazar ka chashma", "lenses")),
                SubcategoryDefinition("sub_physio", "Physiotherapy", "physiotherapy", listOf("physio", "massage")),
                SubcategoryDefinition("sub_ors", "ORS / Hydration", "ors / hydration", listOf("ors", "nimkol", "electrolytes")),
                SubcategoryDefinition("sub_bandages", "Bandages & Creams", "bandages & creams", listOf("crepe bandage", "voltral", "deep heat")),
                SubcategoryDefinition("sub_medical_devices", "Medical Devices", "medical devices", listOf("thermometer", "bp apparatus", "pulse oximeter")),
                SubcategoryDefinition("sub_health_insurance", "Health Insurance", "health insurance", listOf("insurance premium")),
                SubcategoryDefinition("sub_medical_transport", "Medical Transport", "medical transport", listOf("ambulance", "emergency transport"))
            )
        ),

        // 11. Laundry & Domestic Services
        CategoryDefinition(
            id = "cat_laundry",
            name = "Laundry & Domestic Services",
            normalizedName = "laundry & domestic services",
            subcategories = listOf(
                SubcategoryDefinition("sub_laundry_dhobi", "Laundry / Dhobi", "laundry / dhobi", listOf("laundry", "dhobi", "kapray dhulwaye", "wash clothes")),
                SubcategoryDefinition("sub_dry_cleaning", "Dry Cleaning", "dry cleaning", listOf("dry clean", "dry cleaner")),
                SubcategoryDefinition("sub_ironing", "Ironing / Istri", "ironing / istri", listOf("ironing", "istri", "press")),
                SubcategoryDefinition("sub_shoe_cleaning", "Shoe Cleaning", "shoe cleaning", listOf("shoe polish", "mender", "mochi")),
                SubcategoryDefinition("sub_house_cleaning", "House Cleaning", "house cleaning", listOf("safai", "room cleaning")),
                SubcategoryDefinition("sub_domestic_help", "Domestic Help", "domestic help", listOf("maid", "khansama", "cook")),
                SubcategoryDefinition("sub_handyman", "Repairs / Handyman", "repairs / handyman", listOf("handyman", "mistri")),
                SubcategoryDefinition("sub_plumbing", "Plumbing", "plumbing", listOf("plumber", "nal ka kaam")),
                SubcategoryDefinition("sub_elec_services", "Electrical Services", "electrical services", listOf("electrician")),
                SubcategoryDefinition("sub_appliance_repairs", "Appliance Repairs", "appliance repairs", listOf("geyser repair", "ac repair")),
                SubcategoryDefinition("sub_pest_control", "Pest Control", "pest control", listOf("spray", "pest control"))
            )
        ),

        // 12. Tobacco & Related Supplies
        CategoryDefinition(
            id = "cat_tobacco",
            name = "Tobacco & Related Supplies",
            normalizedName = "tobacco & related supplies",
            subcategories = listOf(
                SubcategoryDefinition("sub_cigarettes", "Cigarettes", "cigarettes", listOf("cigarette", "cigarettes", "ciggaretes", "sigrat", "gold leaf", "capstan", "dunhill", "marlboro", "sutta")),
                SubcategoryDefinition("sub_raw_tobacco", "Tobacco", "tobacco", listOf("tobacco", "tambaku", "naswar")),
                SubcategoryDefinition("sub_cigars", "Cigars", "cigars", listOf("cigar", "cigars")),
                SubcategoryDefinition("sub_vape", "Vape Products", "vape products", listOf("vape", "pod", "vape kit")),
                SubcategoryDefinition("sub_vape_refills", "Vape Refills", "vape refills", listOf("e-liquid", "vape flavour", "juice refill")),
                SubcategoryDefinition("sub_nicotine", "Nicotine Products", "nicotine products", listOf("nicotine pouch", "velo")),
                SubcategoryDefinition("sub_lighter", "Lighter", "lighter", listOf("lighter", "clippers", "gas lighter")),
                SubcategoryDefinition("sub_matchbox", "Matchbox / Machis", "matchbox / machis", listOf("machis", "matchbox", "matches")),
                SubcategoryDefinition("sub_smoking_acc", "Smoking Accessories", "smoking accessories", listOf("ashtray", "rolling paper"))
            )
        ),

        // 13. Outdoor & Travel Gear
        CategoryDefinition(
            id = "cat_outdoor",
            name = "Outdoor & Travel Gear",
            normalizedName = "outdoor & travel gear",
            subcategories = listOf(
                SubcategoryDefinition("sub_tent_purchase", "Tent Purchase", "tent purchase", listOf("tent buy", "bought tent", "kheema")),
                SubcategoryDefinition("sub_sleeping_bag", "Sleeping Bag", "sleeping bag", listOf("sleeping bag")),
                SubcategoryDefinition("sub_sleeping_mat", "Sleeping Mat", "sleeping mat", listOf("sleeping mat", "foam")),
                SubcategoryDefinition("sub_camping_stove", "Camping Stove", "camping stove", listOf("camping stove", "chulha", "portable burner")),
                SubcategoryDefinition("sub_gas_canister", "Gas Canister", "gas canister", listOf("gas canister", "small cylinder")),
                SubcategoryDefinition("sub_camping_cookware", "Camping Cookware", "camping cookware", listOf("mess kit", "camping pot")),
                SubcategoryDefinition("sub_torch", "Torch / Headlamp", "torch / headlamp", listOf("torch", "headlamp", "flashlight")),
                SubcategoryDefinition("sub_batteries", "Batteries", "batteries", listOf("cell", "batteries", "duracell")),
                SubcategoryDefinition("sub_power_bank", "Power Bank", "power bank", listOf("power bank", "powerbank")),
                SubcategoryDefinition("sub_nav_gear", "Navigation Gear", "navigation gear", listOf("compass", "gps device")),
                SubcategoryDefinition("sub_rope", "Rope", "rope", listOf("rope", "rassa")),
                SubcategoryDefinition("sub_trekking_poles", "Trekking Poles", "trekking poles", listOf("hiking stick", "trekking poles")),
                SubcategoryDefinition("sub_water_bottle", "Water Bottle / Hydration", "water bottle / hydration", listOf("water bottle", "thermos", "hydration bladder")),
                SubcategoryDefinition("sub_backpack", "Backpack", "backpack", listOf("backpack", "rucksack", "pithu bag")),
                SubcategoryDefinition("sub_waterproof_covers", "Waterproof Covers", "waterproof covers", listOf("rain cover", "dry bag")),
                SubcategoryDefinition("sub_locks", "Locks", "locks", listOf("padlock", "tala", "disc lock")),
                SubcategoryDefinition("sub_repair_tape", "Repair Tape / Glue", "repair tape / glue", listOf("duct tape", "magic tape", "samad bond")),
                SubcategoryDefinition("sub_emergency_supplies", "Emergency Supplies", "emergency supplies", listOf("survival blanket", "whistle"))
            )
        ),

        // 14. Activities & Entertainment
        CategoryDefinition(
            id = "cat_entertainment",
            name = "Activities & Entertainment",
            normalizedName = "activities & entertainment",
            subcategories = listOf(
                SubcategoryDefinition("sub_sightseeing", "Sightseeing", "sightseeing", listOf("sightseeing", "sair sapata")),
                SubcategoryDefinition("sub_museum_tickets", "Museum / Fort Tickets", "museum / fort tickets", listOf("baltit fort ticket", "altit fort ticket", "qila ticket")),
                SubcategoryDefinition("sub_boating", "Boating", "boating", listOf("boating", "attabad boat", "kashti")),
                SubcategoryDefinition("sub_zipline", "Zip-line", "zip-line", listOf("zipline", "bridge crossing")),
                SubcategoryDefinition("sub_guided_tours", "Guided Tours", "guided tours", listOf("tour guide", "guide fee")),
                SubcategoryDefinition("sub_trekking_guide", "Trekking Guide / Porter", "trekking guide / porter", listOf("porter", "trek guide", "coolie")),
                SubcategoryDefinition("sub_local_experiences", "Local Experiences", "local experiences", listOf("cultural event", "folk music")),
                SubcategoryDefinition("sub_cinema", "Cinema", "cinema", listOf("movie ticket", "cinema")),
                SubcategoryDefinition("sub_games", "Games / Sports / Gym", "games / sports / gym", listOf("gym", "snooker", "bowling")),
                SubcategoryDefinition("sub_photography", "Photography Services", "photography services", listOf("drone shoot", "photographer")),
                SubcategoryDefinition("sub_gear_rental", "Equipment Rental", "equipment rental", listOf("jacket rent", "camera rent"))
            )
        ),

        // 15. Fees, Permits & Charges
        CategoryDefinition(
            id = "cat_fees_permits",
            name = "Fees, Permits & Charges",
            normalizedName = "fees, permits & charges",
            subcategories = listOf(
                SubcategoryDefinition("sub_park_permits", "National Park Permits", "national park permits", listOf("national park fee", "khunjerab permit", "deosai entry")),
                SubcategoryDefinition("sub_entry_fees", "Entry Fees", "entry fees", listOf("entry ticket", "entry fee")),
                SubcategoryDefinition("sub_gov_fees", "Government Fees / Passport", "government fees / passport", listOf("passport fee", "nadra fee", "challan")),
                SubcategoryDefinition("sub_bank_fees", "Bank / ATM / Wallet Fees", "bank / atm / wallet fees", listOf("atm charges", "bank deduction", "fed fee", "jazzcash fee")),
                SubcategoryDefinition("sub_fines", "Fines / Traffic Challan", "fines / traffic challan", listOf("challan", "traffic police fine", "warden fine")),
                SubcategoryDefinition("sub_cancellation", "Cancellation Charges", "cancellation charges", listOf("cancellation fee", "penalty"))
            )
        ),

        // 16. Charity & Donations
        CategoryDefinition(
            id = "cat_charity",
            name = "Charity & Donations",
            normalizedName = "charity & donations",
            subcategories = listOf(
                SubcategoryDefinition("sub_sadqa", "Sadqa / Sadaqah", "sadqa / sadaqah", listOf("sadqa", "sadqah", "sadaqah")),
                SubcategoryDefinition("sub_zakat", "Zakat", "zakat", listOf("zakat", "zakaat")),
                SubcategoryDefinition("sub_khairat", "Khairat", "khairat", listOf("khairat", "khairaat")),
                SubcategoryDefinition("sub_masjid", "Masjid / Madrasa Donations", "masjid / madrasa donations", listOf("masjid donation", "chanda", "madrasa")),
                SubcategoryDefinition("sub_welfare", "Welfare Donations", "welfare donations", listOf("edhi", "saylani", "shaukat khanum", "flood relief")),
                SubcategoryDefinition("sub_food_dist", "Food Distribution", "food distribution", listOf("langar", "degh", "rashan")),
                SubcategoryDefinition("sub_general_donation", "General Donations", "general donations", listOf("donation", "chartiy", "charity", "madad"))
            )
        ),

        // 17. Family Support & Gifts
        CategoryDefinition(
            id = "cat_family",
            name = "Family Support & Gifts",
            normalizedName = "family support & gifts",
            subcategories = listOf(
                SubcategoryDefinition("sub_fam_sent", "Family Support Sent", "family support sent", listOf("bhai ko paise diye", "ghar paise bheje", "ammi ko diye", "abu ko diye"), compatibleTypes = listOf("GIFT_SENT", "EXPENSE")),
                SubcategoryDefinition("sub_fam_recv", "Family Support Received", "family support received", listOf("bhai ne paise bheje", "ghar se paise aye", "family funding"), compatibleTypes = listOf("GIFT_RECEIVED", "INCOME")),
                SubcategoryDefinition("sub_gifts_sent", "Gifts Sent", "gifts sent", listOf("gift bheja", "tohfa diya"), compatibleTypes = listOf("GIFT_SENT", "EXPENSE")),
                SubcategoryDefinition("sub_gifts_recv", "Gifts Received", "gifts received", listOf("gift mila", "eidi mili", "inaam"), compatibleTypes = listOf("GIFT_RECEIVED", "INCOME")),
                SubcategoryDefinition("sub_allowance", "Allowance", "allowance", listOf("pocket money", "kharcha mila", "allowance"))
            )
        ),

        // 18. Lending & Borrowing
        CategoryDefinition(
            id = "cat_lending",
            name = "Lending & Borrowing",
            normalizedName = "lending & borrowing",
            subcategories = listOf(
                SubcategoryDefinition("sub_loan_given", "Loan Given", "loan given", listOf("udhaar diya", "udhar diya", "qarz diya", "qaisar ko udhaar 5000 diya", "lent money"), compatibleTypes = listOf("LOAN_GIVEN")),
                SubcategoryDefinition("sub_loan_recv", "Loan Received", "loan received", listOf("udhaar liya", "udhar liya", "qarz liya", "borrowed money", "bhai se udhaar liye"), compatibleTypes = listOf("LOAN_RECEIVED")),
                SubcategoryDefinition("sub_loan_repay_recv", "Loan Repayment Received", "loan repayment received", listOf("udhaar wapas aya", "udhar wapas mila", "qaisar ne udhaar wapas kiya", "debt paid back"), compatibleTypes = listOf("LOAN_REPAYMENT_RECEIVED")),
                SubcategoryDefinition("sub_loan_repay_paid", "Loan Repayment Paid", "loan repayment paid", listOf("udhaar wapas kiya", "qarz ada kiya", "repaid loan"), compatibleTypes = listOf("LOAN_REPAYMENT_PAID"))
            )
        ),

        // 19. Income & Funding
        CategoryDefinition(
            id = "cat_income",
            name = "Income & Funding",
            normalizedName = "income & funding",
            subcategories = listOf(
                SubcategoryDefinition("sub_salary", "Salary", "salary", listOf("salary", "tankhwa", "pay mili", "salary aa gayi"), compatibleTypes = listOf("INCOME")),
                SubcategoryDefinition("sub_freelance", "Freelance Income", "freelance income", listOf("freelance", "upwork payment", "client payment", "fiverr"), compatibleTypes = listOf("INCOME")),
                SubcategoryDefinition("sub_business_receipts", "Business Receipts", "business receipts", listOf("business sale", "dukan ki bikri"), compatibleTypes = listOf("INCOME")),
                SubcategoryDefinition("sub_bonus", "Bonuses & Commission", "bonuses & commission", listOf("bonus mila", "commission"), compatibleTypes = listOf("INCOME")),
                SubcategoryDefinition("sub_reimbursement", "Reimbursement", "reimbursement", listOf("reimbursement", "office se kharcha wapas mila"), compatibleTypes = listOf("INCOME", "REFUND_RECEIVED")),
                SubcategoryDefinition("sub_other_income", "Other Received Funds", "other received funds", listOf("paise aa gaye", "funds received", "payment received"), compatibleTypes = listOf("INCOME"))
            )
        ),

        // 20. Refunds & Recoveries
        CategoryDefinition(
            id = "cat_refunds",
            name = "Refunds & Recoveries",
            normalizedName = "refunds & recoveries",
            subcategories = listOf(
                SubcategoryDefinition("sub_purchase_refund", "Purchase Refund", "purchase refund", listOf("refund mila", "hotel se refund mila", "item returned"), compatibleTypes = listOf("REFUND_RECEIVED")),
                SubcategoryDefinition("sub_deposit_returned", "Deposit Returned", "deposit returned", listOf("security wapas mili", "deposit return"), compatibleTypes = listOf("REFUND_RECEIVED"))
            )
        ),

        // 21. Transfers & Cash Management
        CategoryDefinition(
            id = "cat_transfers",
            name = "Transfers & Cash Management",
            normalizedName = "transfers & cash management",
            subcategories = listOf(
                SubcategoryDefinition("sub_cash_withdrawal", "Cash Withdrawal", "cash withdrawal", listOf("atm se paise nikale", "cash nikala", "atm withdrawal"), compatibleTypes = listOf("TRANSFER_OUT", "TRANSFER_IN")),
                SubcategoryDefinition("sub_own_account", "Own-account Transfer", "own-account transfer", listOf("apne account mein transfer", "bank to cash"), compatibleTypes = listOf("TRANSFER_IN", "TRANSFER_OUT"))
            )
        ),

        // 22. Education & Work
        CategoryDefinition(
            id = "cat_education",
            name = "Education & Work",
            normalizedName = "education & work",
            subcategories = listOf(
                SubcategoryDefinition("sub_courses", "Courses & Books", "courses & books", listOf("course", "kitab", "book", "udemy")),
                SubcategoryDefinition("sub_stationery", "Stationery", "stationery", listOf("pen", "register", "notebook", "stationery")),
                SubcategoryDefinition("sub_tuition", "Tuition / Exam Fees", "tuition / exam fees", listOf("fees", "tuition", "exam fee"))
            )
        ),

        // 23. Electronics & Supplies
        CategoryDefinition(
            id = "cat_electronics",
            name = "Electronics & Supplies",
            normalizedName = "electronics & supplies",
            subcategories = listOf(
                SubcategoryDefinition("sub_phone_accessories", "Phone Accessories", "phone accessories", listOf("cable", "charging cable", "charger", "adapter", "handsfree", "earphones", "airpods")),
                SubcategoryDefinition("sub_storage", "Memory Card / Storage", "memory card / storage", listOf("memory card", "sd card", "usb drive")),
                SubcategoryDefinition("sub_repairs", "Device Repairs", "device repairs", listOf("screen repair", "mobile repair", "laptop repair")),
                SubcategoryDefinition("sub_general_supplies", "General Tools & Supplies", "general tools & supplies", listOf("tape", "wire", "small supplies"))
            )
        ),

        // 24. Miscellaneous
        CategoryDefinition(
            id = "cat_misc",
            name = "Miscellaneous",
            normalizedName = "miscellaneous",
            subcategories = listOf(
                SubcategoryDefinition("sub_unresolved_expense", "Unresolved Expense", "unresolved expense", listOf("kharcha", "miscellaneous")),
                SubcategoryDefinition("sub_unresolved_income", "Unresolved Incoming Funds", "unresolved incoming funds", listOf("unresolved income"), compatibleTypes = listOf("INCOME")),
                SubcategoryDefinition("sub_unresolved_outgoing", "Unresolved Outgoing Funds", "unresolved outgoing funds", listOf("unresolved cash out"), compatibleTypes = listOf("EXPENSE"))
            )
        )
    )

    fun findMatchingSubcategory(text: String): Pair<CategoryDefinition, SubcategoryDefinition>? {
        val lower = text.lowercase().trim()
        for (cat in CATEGORIES) {
            for (sub in cat.subcategories) {
                for (alias in sub.aliases) {
                    if (lower.contains(alias.lowercase())) {
                        return Pair(cat, sub)
                    }
                }
            }
        }
        return null
    }
}
