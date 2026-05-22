// Switch naar de database die de API gebruikt
db = db.getSiblingDB('azaricomm');

// Voeg de providers toe aan de juiste collectie
db.providers.insertMany([
    { _id: "swiftsend", name: "SwiftSend", is_active: true },
    { _id: "legacylink", name: "LegacyLink", is_active: true },
    { _id: "asyncflow", name: "AsyncFlow", is_active: true },
    { _id: "securepost", name: "SecurePost", is_active: true }
]);