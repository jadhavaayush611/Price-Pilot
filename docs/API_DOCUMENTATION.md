# PricePilot REST API Documentation

PricePilot provides a structured, versioned REST API (`/api/v1`) for managing products, sellers, and comparison prices.

---

## Base URL
All API requests are made against the base path:
```
http://localhost:8080/api/v1
```

---

## 1. System Health Check

### Get Health Status
Returns the current health status of the application.

* **Endpoint:** `GET /health`
* **Authentication:** None
* **Response Body:**
  ```json
  {
    "status": "UP"
  }
  ```

---

## 2. Product Search & Comparison

### Advanced Multi-Faceted Search
Search products with keywords, category filters, brand filters, and advanced sorting (by price or discount).

* **Endpoint:** `GET /search`
* **Query Parameters:**
  * `keyword` (string, optional): Matches against product name, brand, category, or description.
  * `category` (string, optional): Filters by exact category name.
  * `brand` (string, optional): Filters by exact brand name.
  * `page` (int, optional, default: `0`): Page index (0-based).
  * `size` (int, optional, default: `10`): Results per page.
  * `sort` (string, optional): Custom sort options:
    * `price-asc` or `price,asc`: Sort by lowest current price ascending.
    * `price-desc` or `price,desc`: Sort by lowest current price descending.
    * `discount-desc` or `discount,desc`: Sort by highest discount percentage.
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

## 3. Product Management

### Create Product
* **Endpoint:** `POST /products`
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
* **Validation Constraints:**
  * `name`: Required, non-blank, max 100 characters.
  * `brand`: Required, non-blank, max 50 characters.
  * `category`: Required, non-blank, max 50 characters.
  * `imageUrl`: Optional, must be a valid URL structure.
* **Success Response (211 Created):**
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
* **Endpoint:** `GET /products`
* **Query Parameters:** `search`, `page`, `size`, `sort`
* **Success Response (200 OK):** Paginated array of products (without full prices arrays to keep list payloads lightweight).

### Get Product By ID
* **Endpoint:** `GET /products/{id}`
* **Success Response (200 OK):** Returns the product details along with its full array of prices and associated seller details.

### Update Product
* **Endpoint:** `PUT /products/{id}`
* **Request Body (JSON):** Same as Create Product.
* **Success Response (200 OK):** Updated product DTO.

### Delete Product
* **Endpoint:** `DELETE /products/{id}`
* **Success Response (204 No Content)**

---

## 4. Seller Management

### Create Seller
* **Endpoint:** `POST /sellers`
* **Request Body (JSON):**
  ```json
  {
    "name": "Best Buy",
    "websiteUrl": "https://bestbuy.com",
    "logoUrl": "https://example.com/bestbuy-logo.svg"
  }
  ```
* **Validation Constraints:**
  * `name`: Required, non-blank.
  * `websiteUrl`: Required, valid URL.
  * `logoUrl`: Optional, valid URL.
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
* **Query Parameters:** `search`, `page`, `size`, `sort`
* **Success Response (200 OK):** Paginated array of sellers.

### Get Seller By ID
* **Endpoint:** `GET /sellers/{id}`

### Update Seller
* **Endpoint:** `PUT /sellers/{id}`

### Delete Seller
* **Endpoint:** `DELETE /sellers/{id}`

---

## 5. Product Price Operations

### Add Product Price
Registers or updates a price point for a product under a specific seller.

* **Endpoint:** `POST /prices`
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
* **Validation Constraints:**
  * `productId`: Required, valid UUID of an existing product.
  * `sellerId`: Required, valid UUID of an existing seller.
  * `currentPrice`: Required, must be >= 0.
  * `originalPrice`: Required, must be > 0.
  * `currentPrice` must be <= `originalPrice` (invalid discount prevention).
  * `productUrl`: Required, valid URL.
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
* **Query Parameters:** `search`, `page`, `size`, `sort`

### Get Price By ID
* **Endpoint:** `GET /prices/{id}`

### Update Product Price
* **Endpoint:** `PUT /prices/{id}`

### Delete Product Price
* **Endpoint:** `DELETE /prices/{id}`

---

## 6. User Authentication

All protected endpoints require an `Authorization` header containing the JWT token: `Authorization: Bearer <token>`.

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

## 7. Personalized Recommendations

### Get Personalized Recommendations
Retrieves recommendations tailored for the logged-in user based on search patterns, views, and favorites.

* **Endpoint:** `GET /recommendations`
* **Authentication:** Required (JWT)
* **Query Parameters:**
  * `category` (string, optional)
  * `brand` (string, optional)
  * `minPrice` (decimal, optional)
  * `maxPrice` (decimal, optional)
  * `sort` (string, optional)
  * `page` (int, default: `0`)
  * `size` (int, default: `10`)
* **Success Response (200 OK):** Paginated array of products.

### Get Similar Products
Retrieves similar products based on a specific product.

* **Endpoint:** `GET /recommendations/similar/{productId}`
* **Authentication:** None
* **Query Parameters:**
  * `limit` (int, default: `10`)
* **Success Response (200 OK):** List of similar product DTOs.

### Get Trending Products
Retrieves trending products based on overall view count and price drop analytics.

* **Endpoint:** `GET /recommendations/trending`
* **Authentication:** None
* **Query Parameters:**
  * `limit` (int, default: `10`)
* **Success Response (200 OK):** List of trending product DTOs.

---

## 8. AI Assistant Chatbot

Interact with the AI shopping assistant chatbot, leveraging LLMs to answer comparison queries, search parameters, or shopping advice.

### Chat with Assistant
* **Endpoint:** `POST /assistant/chat`
* **Authentication:** Required (JWT)
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
* **Authentication:** Required (JWT)
* **Request Body (JSON):** Same as Chat.

### Ask Question
* **Endpoint:** `POST /assistant/ask`
* **Authentication:** Required (JWT)
* **Request Body (JSON):** Same as Chat.

### Clear Chat Memory
* **Endpoint:** `POST /assistant/clear_memory`
* **Authentication:** Required (JWT)
* **Request Body (JSON):** Empty or user data.
* **Success Response (200 OK):** Memory cleared indicator.

---

## 9. Exception & Error Handling
PricePilot returns consistent JSON error payloads for client errors or internal issues:

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
