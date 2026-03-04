##  Android Application – Anti Food Waste 

This version improves the first version of the application by introducing **connected features** that make the system more interactive and collaborative.  
The application allows clients to reserve and pay for food baskets online, while merchants can manage their offers and reservations in real time.

The main objective remains to **reduce food waste** by connecting clients with commerces that have unsold food baskets.

---

##  Application Actors

### Client
- Browse commerces and available baskets in real time
- Reserve and pay for food baskets
- Receive notifications about reservations and available baskets
- View reservation history online
- Cancel reservations

### Merchant
- Manage baskets online
- Track reservations in real time
- Receive notifications when a reservation is made
- Access simple statistics (sold baskets, revenue)

### Admin
- Supervise the application
- Validate commerces
- Manage users if necessary

---

##  Online Authentication

The application uses **online authentication services**, such as **Firebase Authentication**, with the following features:

- User registration and login
- Role management (**Client / Merchant / Admin**)
- Secure session management
- Cloud synchronization

---

##  Client Features

Clients can:

- View commerces and baskets in real time
- Reserve food baskets online
- Pay for reservations through integrated payment
- Receive notifications about new baskets or confirmed reservations
- Access reservation history stored in the cloud
- Cancel reservations online

---

##  Merchant Features

Merchants can:

- Add, update, or delete baskets in real time
- View all received reservations instantly
- Receive notifications for each reservation
- Access simple statistics about sales and activity

---

##  Database Architecture

This version uses a **hybrid database system**:

- **Cloud Database:** Firebase Firestore (or similar)
- **Local Database:** Room (for offline access)

### Data Synchronization

The application synchronizes data automatically between the **local database** and the **cloud database** to ensure a smooth user experience even when the device is offline.

### Main Tables

- `User`
- `Commerce`
- `Panier`
- `Reservation`

### Database Relationships

- **User (1) → (N) Reservation**
- **Commerce (1) → (N) Panier**
- **Panier (1) → (N) Reservation**

---
