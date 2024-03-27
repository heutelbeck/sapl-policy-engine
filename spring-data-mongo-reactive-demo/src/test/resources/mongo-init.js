db = db.getSiblingDB('sapl4db');

db.createCollection('users');

db.users.insertMany([ {
    "_id" : ObjectId("64de2fb8375aabd24878daa4"),
    "firstname" : "Malinda",
    "lastname" : "Perrot",
    "age" : 53,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de3bd9fbf82799677ed336"),
    "firstname" : "Emerson",
    "lastname" : "Rowat",
    "age" : 82,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de3bd9fbf82799677ed337"),
    "firstname" : "Yul",
    "lastname" : "Barukh",
    "age" : 79,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de3bd9fbf82799677ed338"),
    "firstname" : "Terrel",
    "lastname" : "Woodings",
    "age" : 96,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de3bd9fbf82799677ed339"),
    "firstname" : "Martino",
    "lastname" : "Bartolijn",
    "age" : 33,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de3bd9fbf82799677ed33a"),
    "firstname" : "Konstantine",
    "lastname" : "Hampton",
    "age" : 96,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de3bd9fbf82799677ed33b"),
    "firstname" : "Cathleen",
    "lastname" : "Simms",
    "age" : 25,
    "role" : "ADMIN",
    "active" : false
}, {
    "_id" : ObjectId("64de3bd9fbf82799677ed33c"),
    "firstname" : "Adolphe",
    "lastname" : "Streeton",
    "age" : 46,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de3bd9fbf82799677ed33d"),
    "firstname" : "Alessandro",
    "lastname" : "Tomaskov",
    "age" : 64,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de3bd9fbf82799677ed33e"),
    "firstname" : "Hobie",
    "lastname" : "Maddinon",
    "age" : 32,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de3bd9fbf82799677ed33f"),
    "firstname" : "Franni",
    "lastname" : "Mingey",
    "age" : 57,
    "role" : "ADMIN",
    "active" : false
}, {
    "_id" : ObjectId("64de3bd9fbf82799677ed340"),
    "firstname" : "Giraldo",
    "lastname" : "Scade",
    "age" : 83,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de3bd9fbf82799677ed341"),
    "firstname" : "Pooh",
    "lastname" : "Cocks",
    "age" : 19,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de3bd9fbf82799677ed342"),
    "firstname" : "Mario",
    "lastname" : "Albinson",
    "age" : 54,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de3bd9fbf82799677ed343"),
    "firstname" : "Olav",
    "lastname" : "Hoopper",
    "age" : 32,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de3bd9fbf82799677ed344"),
    "firstname" : "Tuckie",
    "lastname" : "Morfell",
    "age" : 35,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de3bd9fbf82799677ed345"),
    "firstname" : "Sylas",
    "lastname" : "Bickerstasse",
    "age" : 66,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de3bd9fbf82799677ed346"),
    "firstname" : "Kacey",
    "lastname" : "Angell",
    "age" : 94,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de3bd9fbf82799677ed347"),
    "firstname" : "Dame",
    "lastname" : "Negri",
    "age" : 67,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de3bd9fbf82799677ed348"),
    "firstname" : "Perren",
    "lastname" : "Durtnall",
    "age" : 75,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09cccf4"),
    "firstname" : "Katleen",
    "lastname" : "Capstaff",
    "age" : 82,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09cccf5"),
    "firstname" : "Candis",
    "lastname" : "Souza",
    "age" : 90,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09cccf6"),
    "firstname" : "Mac",
    "lastname" : "Deetlof",
    "age" : 55,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09cccf7"),
    "firstname" : "Tabby",
    "lastname" : "Skittreal",
    "age" : 93,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09cccf8"),
    "firstname" : "Adriano",
    "lastname" : "Tennet",
    "age" : 60,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09cccf9"),
    "firstname" : "Cameron",
    "lastname" : "Garnham",
    "age" : 39,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09cccfa"),
    "firstname" : "Jeri",
    "lastname" : "Toppin",
    "age" : 79,
    "role" : "ADMIN",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09cccfb"),
    "firstname" : "Josselyn",
    "lastname" : "Tongs",
    "age" : 34,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09cccfc"),
    "firstname" : "Reynolds",
    "lastname" : "Buesnel",
    "age" : 55,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09cccfd"),
    "firstname" : "Hedwig",
    "lastname" : "Berrill",
    "age" : 66,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09cccfe"),
    "firstname" : "Karylin",
    "lastname" : "Schule",
    "age" : 82,
    "role" : "ADMIN",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09cccff"),
    "firstname" : "Thaddeus",
    "lastname" : "Machin",
    "age" : 50,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd00"),
    "firstname" : "Rhody",
    "lastname" : "Vasilic",
    "age" : 72,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd01"),
    "firstname" : "Josy",
    "lastname" : "Skein",
    "age" : 58,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd02"),
    "firstname" : "Rachael",
    "lastname" : "Baukham",
    "age" : 57,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd03"),
    "firstname" : "Claudianus",
    "lastname" : "Courtois",
    "age" : 98,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd04"),
    "firstname" : "Imelda",
    "lastname" : "Gilkes",
    "age" : 65,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd05"),
    "firstname" : "Marcelia",
    "lastname" : "Elmer",
    "age" : 24,
    "role" : "ADMIN",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd06"),
    "firstname" : "Lib",
    "lastname" : "Peschka",
    "age" : 89,
    "role" : "ADMIN",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd07"),
    "firstname" : "Winna",
    "lastname" : "Shellshear",
    "age" : 46,
    "role" : "ADMIN",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd08"),
    "firstname" : "Patty",
    "lastname" : "O Mahoney",
    "age" : 52,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd09"),
    "firstname" : "Jerrylee",
    "lastname" : "Lukas",
    "age" : 76,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd0a"),
    "firstname" : "Fielding",
    "lastname" : "MacGibbon",
    "age" : 53,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd0b"),
    "firstname" : "Tuckie",
    "lastname" : "Hugett",
    "age" : 29,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd0c"),
    "firstname" : "Penrod",
    "lastname" : "Munehay",
    "age" : 70,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd0d"),
    "firstname" : "Lexine",
    "lastname" : "Blakden",
    "age" : 92,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd0e"),
    "firstname" : "Petra",
    "lastname" : "Shackleford",
    "age" : 97,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd0f"),
    "firstname" : "Glenn",
    "lastname" : "Stennes",
    "age" : 45,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd10"),
    "firstname" : "Morry",
    "lastname" : "Wolfer",
    "age" : 23,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd11"),
    "firstname" : "Dianemarie",
    "lastname" : "Howgill",
    "age" : 69,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd12"),
    "firstname" : "Darline",
    "lastname" : "Hinsche",
    "age" : 39,
    "role" : "ADMIN",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd13"),
    "firstname" : "Lou",
    "lastname" : "Kiossel",
    "age" : 62,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd14"),
    "firstname" : "Titus",
    "lastname" : "Gillbard",
    "age" : 38,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd15"),
    "firstname" : "Henderson",
    "lastname" : "Enticknap",
    "age" : 94,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd16"),
    "firstname" : "Leonie",
    "lastname" : "Miranda",
    "age" : 65,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd17"),
    "firstname" : "Amber",
    "lastname" : "Pink",
    "age" : 50,
    "role" : "ADMIN",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd18"),
    "firstname" : "Forrest",
    "lastname" : "Izzett",
    "age" : 62,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd19"),
    "firstname" : "Lenette",
    "lastname" : "Fuster",
    "age" : 42,
    "role" : "ADMIN",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd1a"),
    "firstname" : "Hazel",
    "lastname" : "Alston",
    "age" : 77,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd1b"),
    "firstname" : "Aldrich",
    "lastname" : "Maymond",
    "age" : 24,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd1c"),
    "firstname" : "Harmon",
    "lastname" : "Foulis",
    "age" : 30,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd1d"),
    "firstname" : "Mandy",
    "lastname" : "Fain",
    "age" : 66,
    "role" : "ADMIN",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd1e"),
    "firstname" : "Sonnie",
    "lastname" : "Dilston",
    "age" : 32,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd1f"),
    "firstname" : "Wynne",
    "lastname" : "MacDearmaid",
    "age" : 73,
    "role" : "ADMIN",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd20"),
    "firstname" : "Hyatt",
    "lastname" : "Cron",
    "age" : 43,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd21"),
    "firstname" : "Theodosia",
    "lastname" : "Zorzenoni",
    "age" : 34,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd22"),
    "firstname" : "Carleton",
    "lastname" : "Keyson",
    "age" : 79,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd23"),
    "firstname" : "Byran",
    "lastname" : "Dumbare",
    "age" : 22,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd24"),
    "firstname" : "Deina",
    "lastname" : "Watting",
    "age" : 92,
    "role" : "ADMIN",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd25"),
    "firstname" : "Thacher",
    "lastname" : "Folca",
    "age" : 18,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd26"),
    "firstname" : "Gayle",
    "lastname" : "Orneles",
    "age" : 82,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd27"),
    "firstname" : "Ernestine",
    "lastname" : "Hatch",
    "age" : 82,
    "role" : "ADMIN",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd28"),
    "firstname" : "Jonie",
    "lastname" : "Delle",
    "age" : 95,
    "role" : "ADMIN",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd29"),
    "firstname" : "Lin",
    "lastname" : "Burleigh",
    "age" : 72,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd2a"),
    "firstname" : "Olva",
    "lastname" : "Ridding",
    "age" : 85,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd2b"),
    "firstname" : "Gray",
    "lastname" : "Ashall",
    "age" : 39,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd2c"),
    "firstname" : "Lorant",
    "lastname" : "Busch",
    "age" : 87,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd2d"),
    "firstname" : "Pryce",
    "lastname" : "Mosedill",
    "age" : 46,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd2e"),
    "firstname" : "Dori",
    "lastname" : "Norcutt",
    "age" : 37,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd2f"),
    "firstname" : "Amy",
    "lastname" : "Gurnett",
    "age" : 99,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd30"),
    "firstname" : "Lauraine",
    "lastname" : "Doogood",
    "age" : 54,
    "role" : "ADMIN",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd31"),
    "firstname" : "Casper",
    "lastname" : "Upstell",
    "age" : 85,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd32"),
    "firstname" : "Lynnett",
    "lastname" : "Malloch",
    "age" : 62,
    "role" : "ADMIN",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd33"),
    "firstname" : "Edi",
    "lastname" : "Giacopelo",
    "age" : 94,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd34"),
    "firstname" : "Bryanty",
    "lastname" : "Arnaud",
    "age" : 84,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd35"),
    "firstname" : "Nolana",
    "lastname" : "Masdon",
    "age" : 72,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd36"),
    "firstname" : "Jodi",
    "lastname" : "Corah",
    "age" : 58,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd37"),
    "firstname" : "Jazmin",
    "lastname" : "Sheehan",
    "age" : 45,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd38"),
    "firstname" : "Brandie",
    "lastname" : "Bushrod",
    "age" : 42,
    "role" : "ADMIN",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd39"),
    "firstname" : "Darwin",
    "lastname" : "Atwill",
    "age" : 28,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd3a"),
    "firstname" : "Cicely",
    "lastname" : "Dearsley",
    "age" : 70,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd3b"),
    "firstname" : "Redford",
    "lastname" : "Palphreyman",
    "age" : 90,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd3c"),
    "firstname" : "Jany",
    "lastname" : "Lambourne",
    "age" : 92,
    "role" : "ADMIN",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd3d"),
    "firstname" : "Tod",
    "lastname" : "Siddaley",
    "age" : 69,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd3e"),
    "firstname" : "Delinda",
    "lastname" : "Jerzykiewicz",
    "age" : 82,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd3f"),
    "firstname" : "Hobart",
    "lastname" : "Strand",
    "age" : 54,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd40"),
    "firstname" : "Erastus",
    "lastname" : "Spoure",
    "age" : 49,
    "role" : "USER",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd41"),
    "firstname" : "Eydie",
    "lastname" : "Orys",
    "age" : 64,
    "role" : "ADMIN",
    "active" : true
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd42"),
    "firstname" : "Bastian",
    "lastname" : "Dearden",
    "age" : 32,
    "role" : "USER",
    "active" : false
}, {
    "_id" : ObjectId("64de594d36d30786c09ccd43"),
    "firstname" : "Cassondra",
    "lastname" : "Colbridge",
    "age" : 73,
    "role" : "ADMIN",
    "active" : true
} ]);