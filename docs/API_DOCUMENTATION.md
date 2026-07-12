# PricePilot REST API Documentation

PricePilot provides a structured, versioned REST API (`/api/v1`) for managing products, sellers, comparison prices, watchlists, saved products, AI assistant chat, and ML datasets.

---

## Base URL
All API requests are made against the base path:
```
http://localhost:8080/api/v1
```

## Authentication
Protected endpoints require an `Authorization` header containing a JSON Web Token (JWT) issued during registration or login:
```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsIn...
```

---

## Table of Contents
1. [System Health Check](#1-system-health-check)
2. [User Authentication](#2-user-authentication)
3. [Product Management](#3-product-management)
4. [Search & Comparison](#4-search--comparison)
5. [Seller Management](#5-seller-management)
6. [Product Price Operations](#6-product-price-operations)
7. [Personalized Recommendations](#7-personalized-recommendations)
8. [User Dashboard](#8-user-dashboard)
9. [Price Watchlists](#9-price-watchlists)
10. [Saved Products](#10-saved-products)
11. [Product Analytics](#11-product-analytics)
12. [AI Assistant Chatbot](#12-ai-assistant-chatbot)
13. [Dataset Export Admin APIs](#13-dataset-export-admin-apis)
14. [Machine Learning Admin APIs](#14-machine-learning-admin-apis)
15. [Exception & Error Handling](#15-exception--error-handling)

---

## 1. System Health Check

### Get Health Status
Returns the current health status of the application.

* **Endpoint:** `GET /health`
* **Authentication:** None
* **Success Response (200 OK):**
  ```json
  {
    "status": "UP"
  }
  ```

---

## 2. User Authentication

### Register User
Creates a new user account and returns a JWT token.

* **Endpoint:** `POST /auth/register`
* **Authentication:** None
* **Request Body (JSON):**
  ```json
  {
    "firstName": "Smoke",
    "lastName": "Tester",
    "email": "smoketest@example.com",
    "password": "Password123!"
  }
  ```
* **Success Response (201 Created):**
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsIn...",
    "user": {
      "id": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "firstName": "Smoke",
      "lastName": "Tester",
      "email": "smoketest@example.com",
      "role": "USER",
      "createdAt": "2026-07-12T07:54:07Z",
      "updatedAt": "2026-07-12T07:54:07Z"
    }
  }
  ```

### Login User
Authenticates credentials and returns a JWT token.

* **Endpoint:** `POST /auth/login`
* **Authentication:** None
* **Request Body (JSON):**
  ```json
  {
    "email": "smoketest@example.com",
    "password": "Password123!"
  }
  ```
* **Success Response (200 OK):** Same as Register User.

---

## 3. Product Management

### Create Product
* **Endpoint:** `POST /products`
* **Authentication:** Required (Admin)
* **Request Body (JSON):**
  ```json
  {
    "name": "iPhone 15 Pro Max",
    "brand": "Apple",
    "category": "Electronics",
    "description": "256GB Space Black.",
    "imageUrl": "https://example.com/iphone15.jpg"
  }
  ```
* **Success Response (201 Created):**
  ```json
  {
    "id": "8b4c5d6e-7f8a-9b0c-1d2e-3f4a5b6c7d8e",
    "name": "iPhone 15 Pro Max",
    "brand": "Apple",
    "category": "Electronics",
    "description": "256GB Space Black.",
    "imageUrl": "https://example.com/iphone15.jpg",
    "createdAt": "2026-06-21T22:33:00Z",
    "updatedAt": "2026-06-21T22:33:00Z"
  }
  ```

### Get All Products
Returns paginated list of products.

* **Endpoint:** `GET /products`
* **Authentication:** None
* **Query Parameters:** `page` (int), `size` (int), `sort` (string)
* **Success Response (200 OK):** Paginated array of products.

### Get Product By ID
* **Endpoint:** `GET /products/{id}`
* **Authentication:** None
* **Success Response (200 OK):** Returns product details along with its full array of prices and associated seller details.

### Update Product
* **Endpoint:** `PUT /products/{id}`
* **Authentication:** Required (Admin)
* **Request Body (JSON):** Same as Create Product.
* **Success Response (200 OK):** Updated product DTO.

### Delete Product
* **Endpoint:** `DELETE /products/{id}`
* **Authentication:** Required (Admin)
* **Success Response (204 No Content)**

---

## 4. Search & Comparison

### Advanced Multi-Faceted Search
Search products with keywords, category filters, brand filters, and advanced sorting.

* **Endpoint:** `GET /search`
* **Authentication:** None
* **Query Parameters:**
  * `keyword` (string, optional): Matches product name, brand, category, or description.
  * `category` (string, optional): Filters by exact category name.
  * `brand` (string, optional): Filters by exact brand name.
  * `page` (int, optional, default: `0`)
  * `size` (int, optional, default: `10`)
  * `sort` (string, optional): `price-asc`, `price-desc`, `discount-desc`.
* **Success Response (200 OK):**
  ```json
  {
    "content": [
      {
        "id": "7a3b4c5d-6e7f-8a9b-0c1d-2e3f4a5b6c7d",
        "name": "Sony WH-1000XM5 Wireless Headphones",
        "brand": "Sony",
        "description": "Industry-leading noise canceling over-ear headphones.",
        "category": "Electronics",
        "imageUrl": "https://example.com/sony-xm5.jpg",
        "lowestPrice": 328.00,
        "highestPrice": 399.00,
        "prices": [
          {
            "id": "e3f4a5b6-7c8d-9e0f-1a2b-3c4d5e6f7a8b",
            "currentPrice": 328.00,
            "originalPrice": 399.00,
            "discountPercentage": 17.79,
            "productUrl": "https://amazon.com/sony-xm5",
            "lastUpdated": "2026-06-21T22:30:20Z",
            "seller": {
              "id": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
              "name": "Amazon",
              "websiteUrl": "https://amazon.com",
              "logoUrl": "https://example.com/amazon-logo.svg"
            }
          }
        ],
        "createdAt": "2026-06-21T18:00:00Z",
        "updatedAt": "2026-06-21T18:00:00Z"
      }
    ],
    "totalPages": 1,
    "totalElements": 1,
    "size": 10,
    "number": 0
  }
  ```

---

## 5. Seller Management

### Create Seller
* **Endpoint:** `POST /sellers`
* **Authentication:** Required (Admin)
* **Request Body (JSON):**
  ```json
  {
    "name": "Best Buy",
    "websiteUrl": "https://bestbuy.com",
    "logoUrl": "https://example.com/bestbuy-logo.svg"
  }
  ```
* **Success Response (201 Created):**
  ```json
  {
    "id": "9c4d5e6f-7a8b-9c0d-1e2f-3a4b5c6d7e8f",
    "name": "Best Buy",
    "websiteUrl": "https://bestbuy.com",
    "logoUrl": "https://example.com/bestbuy-logo.svg",
    "createdAt": "2026-06-21T22:34:00Z",
    "updatedAt": "2026-06-21T22:34:00Z"
  }
  ```

### Get All Sellers
* **Endpoint:** `GET /sellers`
* **Authentication:** None
* **Success Response (200 OK):** Paginated list of sellers.

### Get Seller By ID
* **Endpoint:** `GET /sellers/{id}`
* **Authentication:** None
* **Success Response (200 OK):** Seller details.

### Update Seller
* **Endpoint:** `PUT /sellers/{id}`
* **Authentication:** Required (Admin)
* **Request Body (JSON):** Same as Create Seller.
* **Success Response (200 OK):** Updated seller details.

### Delete Seller
* **Endpoint:** `DELETE /sellers/{id}`
* **Authentication:** Required (Admin)
* **Success Response (204 No Content)**

---

## 6. Product Price Operations

### Add Product Price
Registers or updates a price point for a product under a specific seller.

* **Endpoint:** `POST /prices`
* **Authentication:** Required (Admin)
* **Request Body (JSON):**
  ```json
  {
    "productId": "8b4c5d6e-7f8a-9b0c-1d2e-3f4a5b6c7d8e",
    "sellerId": "9c4d5e6f-7a8b-9c0d-1e2f-3a4b5c6d7e8f",
    "currentPrice": 999.00,
    "originalPrice": 1099.00,
    "productUrl": "https://bestbuy.com/iphone-15"
  }
  ```
* **Success Response (201 Created):**
  ```json
  {
    "id": "0d1e2f3a-4b5c-6d7e-8f9a-0b1c2d3e4f5a",
    "currentPrice": 999.00,
    "originalPrice": 1099.00,
    "discountPercentage": 9.10,
    "productUrl": "https://bestbuy.com/iphone-15",
    "lastUpdated": "2026-06-21T22:35:00Z",
    "createdAt": "2026-06-21T22:35:00Z",
    "updatedAt": "2026-06-21T22:35:00Z"
  }
  ```

### Get All Product Prices
* **Endpoint:** `GET /prices`
* **Authentication:** None
* **Success Response (200 OK):** Paginated price points.

### Get Price By ID
* **Endpoint:** `GET /prices/{id}`
* **Authentication:** None
* **Success Response (200 OK)**

### Update Product Price
* **Endpoint:** `PUT /prices/{id}`
* **Authentication:** Required (Admin)
* **Request Body (JSON):** Same as Add Product Price.
* **Success Response (200 OK)**

### Delete Product Price
* **Endpoint:** `DELETE /prices/{id}`
* **Authentication:** Required (Admin)
* **Success Response (204 No Content)**

---

## 7. Personalized Recommendations

### Get Personalized Recommendations
Retrieves recommendations tailored for the logged-in user based on search patterns, views, and favorites.

* **Endpoint:** `GET /recommendations`
* **Authentication:** Required (User)
* **Query Parameters:** `category` (string, optional), `brand` (string, optional), `page` (int), `size` (int)
* **Success Response (200 OK):** Paginated array of recommended products.

### Get Similar Products
* **Endpoint:** `GET /recommendations/similar/{productId}`
* **Authentication:** None
* **Query Parameters:** `limit` (int, default: `10`)
* **Success Response (200 OK):** List of similar product objects.

### Get Trending Products
* **Endpoint:** `GET /recommendations/trending`
* **Authentication:** None
* **Query Parameters:** `limit` (int, default: `10`)
* **Success Response (200 OK):** List of trending products.

---

## 8. User Dashboard

### Get Dashboard Data
Returns a complete dashboard summary for the authenticated user, including activity logs, recommendation feeds, watchlists, saved products, and trending lists.

* **Endpoint:** `GET /dashboard`
* **Authentication:** Required (User)
* **Success Response (200 OK):**
  ```json
  {
    "firstName": "Smoke",
    "lastName": "Tester",
    "email": "smoketest@example.com",
    "role": "USER",
    "savedCount": 1,
    "watchlistCount": 1,
    "totalActivitiesCount": 3,
    "activePriceAlertsCount": 1,
    "recommendations": [ ... ],
    "recentlyViewed": [ ... ],
    "priceDropAlerts": [ ... ],
    "trendingProducts": [ ... ],
    "watchlists": [ ... ],
    "savedProducts": [ ... ],
    "recentActivity": [
      {
        "id": "e4f5a6b7-c8d9-0e1f-2a3b-4c5d6e7f8a9b",
        "eventType": "PRODUCT_VIEW",
        "createdAt": "2026-07-12T07:55:00Z"
      }
    ],
    "recentSearches": ["headphones"],
    "mostClickedSellers": [
      {
        "sellerName": "Amazon",
        "clickCount": 1
      }
    ]
  }
  ```

---

## 9. Price Watchlists

### Create Watchlist Item
Add a product tracker with a target price.

* **Endpoint:** `POST /watchlists`
* **Authentication:** Required (User)
* **Request Body (JSON):**
  ```json
  {
    "productId": "8b4c5d6e-7f8a-9b0c-1d2e-3f4a5b6c7d8e",
    "targetPrice": 299.99
  }
  ```
* **Success Response (201 Created):**
  ```json
  {
    "id": "c7d8e9f0-1a2b-3c4d-5e6f-7a8b9c0d1e2f",
    "productId": "8b4c5d6e-7f8a-9b0c-1d2e-3f4a5b6c7d8e",
    "productName": "Sony WH-1000XM5 Wireless Headphones",
    "brand": "Sony",
    "imageUrl": "https://example.com/sony-xm5.jpg",
    "targetPrice": 299.99,
    "currentBestPrice": 328.00,
    "priceDifference": 28.01,
    "active": true,
    "createdAt": "2026-07-12T08:00:00Z",
    "updatedAt": "2026-07-12T08:00:00Z"
  }
  ```

### Get All Watchlists
* **Endpoint:** `GET /watchlists`
* **Authentication:** Required (User)
* **Success Response (200 OK):** Array of watchlist items belonging to the active user.

### Get Watchlist Item By ID
* **Endpoint:** `GET /watchlists/{id}`
* **Authentication:** Required (User)
* **Success Response (200 OK):** Watchlist item details.

### Update Watchlist Item
Update the target price or enable/disable state.

* **Endpoint:** `PUT /watchlists/{id}`
* **Authentication:** Required (User)
* **Request Body (JSON):**
  ```json
  {
    "targetPrice": 280.00,
    "active": true
  }
  ```
* **Success Response (200 OK):** Updated watchlist item details.

### Delete Watchlist Item
* **Endpoint:** `DELETE /watchlists/{id}`
* **Authentication:** Required (User)
* **Success Response (204 No Content)**

---

## 10. Saved Products

### Save Product to Favorites
* **Endpoint:** `POST /users/saved-products/{productId}`
* **Authentication:** Required (User)
* **Success Response (201 Created):** Empty body.

### Unsave Product
* **Endpoint:** `DELETE /users/saved-products/{productId}`
* **Authentication:** Required (User)
* **Success Response (204 No Content):** Empty body.

### Get Saved Products List
* **Endpoint:** `GET /users/saved-products`
* **Authentication:** Required (User)
* **Success Response (200 OK):**
  ```json
  [
    {
      "productId": "8b4c5d6e-7f8a-9b0c-1d2e-3f4a5b6c7d8e",
      "name": "Sony WH-1000XM5 Wireless Headphones",
      "brand": "Sony",
      "category": "Electronics",
      "imageUrl": "https://example.com/sony-xm5.jpg",
      "bestPrice": 328.00,
      "savedAt": "2026-07-12T08:05:00Z"
    }
  ]
  ```

---

## 11. Product Analytics

### Get Product Analytics Stats
Returns interaction stats for a single product.

* **Endpoint:** `GET /analytics/products/{productId}`
* **Authentication:** Required (User)
* **Success Response (200 OK):**
  ```json
  {
    "productId": "8b4c5d6e-7f8a-9b0c-1d2e-3f4a5b6c7d8e",
    "viewCount": 125,
    "saveCount": 42,
    "watchlistCount": 18,
    "priceChangeCount": 4,
    "trendingScore": 8.75
  }
  ```

---

## 12. AI Assistant Chatbot

### Chat with Assistant
Provide query message to prompt chatbot evaluation.

* **Endpoint:** `POST /assistant/chat`
* **Authentication:** Required (User)
* **Request Body (JSON):**
  ```json
  {
    "message": "Compare Sony XM5 and Bose QuietComfort headphones."
  }
  ```
* **Success Response (200 OK):**
  ```json
  {
    "response": "Here is the comparison between Sony WH-1000XM5 and Bose QuietComfort..."
  }
  ```

### Compare Products
* **Endpoint:** `POST /assistant/compare`
* **Authentication:** Required (User)
* **Request Body (JSON):** Same as Chat.

### Ask Question
* **Endpoint:** `POST /assistant/ask`
* **Authentication:** Required (User)
* **Request Body (JSON):** Same as Chat.

### Clear Chat Memory
* **Endpoint:** `POST /assistant/clear_memory`
* **Authentication:** Required (User)
* **Success Response (200 OK):** Memory cleared indicator.

---

## 13. Dataset Export Admin APIs

These endpoints are used by ML training processes to export raw relational database records in JSON or CSV.

* **Endpoints:**
  * `GET /datasets/products`
  * `GET /datasets/product-analytics`
  * `GET /datasets/interaction-events`
  * `GET /datasets/watchlists`
  * `GET /datasets/saved-products`
  * `GET /datasets/search-history`
  * `GET /datasets/dashboard-summary`
  * `GET /datasets/price-history`
* **Authentication:** Required (Admin)
* **Query Parameters:** `format` (`json` or `csv`), filters (date range, user/product IDs), pagination.
* **Success Response (200 OK):** Paginated JSON or downloadable CSV file.

---

## 14. Machine Learning Admin APIs

Used to orchestrate training, check model details, or predict outcomes.

### Trigger Pipeline Training
* **Endpoint:** `POST /ml/train`
* **Authentication:** Required (Admin)
* **Request Body (JSON):**
  ```json
  {
    "datasetVersion": "1.0.0",
    "k": 10
  }
  ```
* **Success Response (200 OK):** Model evaluation metrics summary.

### Check Model Metadata
* **Endpoint:** `GET /ml/metadata/{algorithm}`
* **Authentication:** Required (Admin)
* **Success Response (200 OK):** Model parameters, trained date, and metrics.

### Generate Collaborative predictions
* **Endpoint:** `GET /ml/predict/{algorithm}`
* **Authentication:** Required (Admin)
* **Query Parameters:** `userId` (UUID), `limit` (int)
* **Success Response (200 OK):** List of recommendations.

---

## 15. Exception & Error Handling

### Example: Validation Error (400 Bad Request)
```json
{
  "timestamp": "2026-06-21T22:36:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/products",
  "validationErrors": [
    {
      "field": "name",
      "message": "must not be blank"
    }
  ]
}
```

### Example: Entity Not Found (404 Not Found)
```json
{
  "timestamp": "2026-06-21T22:36:30Z",
  "status": 404,
  "error": "Not Found",
  "message": "Product not found with id: 8b4c5d6e-7f8a-9b0c-1d2e-3f4a5b6c7d8e",
  "path": "/api/v1/products/8b4c5d6e-7f8a-9b0c-1d2e-3f4a5b6c7d8e"
}
```
