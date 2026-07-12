# Price History Tracking System Architecture

This document provides a comprehensive overview of the design, implementation, and future analytic paths for the **Price History Tracking** feature in the PricePilot platform.

---

## 1. ER Diagram Updates

The `price_histories` table stores immutable historical records of product price changes. It connects product and seller entities with a many-to-one mapping.

### Database Relation Schema (Mermaid)

```mermaid
erDiagram
    products ||--o{ price_histories : has_history
    sellers ||--o{ price_histories : provides_history
    products ||--o{ product_prices : has_prices
    sellers ||--o{ product_prices : offers

    products {
        uuid id PK
        string name
        string brand
        string category
        string description
        string image_url
        boolean archived
        timestamp created_at
        timestamp updated_at
    }

    sellers {
        uuid id PK
        string name
        string website_url
        string logo_url
        timestamp created_at
        timestamp updated_at
    }

    price_histories {
        uuid id PK
        uuid product_id FK
        uuid seller_id FK
        numeric old_price
        numeric new_price
        numeric price_difference
        numeric change_percentage
        timestamp changed_at
        timestamp created_at
        timestamp updated_at
    }

    product_prices {
        uuid id PK
        uuid product_id FK
        uuid seller_id FK
        numeric current_price
        numeric original_price
        numeric discount_percentage
        string product_url
        timestamp last_updated
        timestamp created_at
        timestamp updated_at
    }
```

### DDL Schema (SQL representation)

```sql
CREATE TABLE price_histories (
    id UUID NOT NULL PRIMARY KEY,
    product_id UUID NOT NULL,
    seller_id UUID NOT NULL,
    old_price DECIMAL(10, 2) NOT NULL,
    new_price DECIMAL(10, 2) NOT NULL,
    price_difference DECIMAL(10, 2) NOT NULL,
    change_percentage DECIMAL(5, 2) NOT NULL,
    changed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_price_histories_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_price_histories_seller FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE CASCADE
);

CREATE INDEX idx_price_histories_product_id ON price_histories(product_id);
CREATE INDEX idx_price_histories_seller_id ON price_histories(seller_id);
CREATE INDEX idx_price_histories_changed_at ON price_histories(changed_at DESC);
```

> [!NOTE]
> - Indexing `changed_at DESC` guarantees sub-millisecond retrieval speeds during sorted pagination.
> - Eager join fetching (`JOIN FETCH`) is utilized in repositories to prevent N+1 queries.

---

## 2. Price Change Lifecycle & Flow

Price history tracking is managed at the Service layer, avoiding database triggers or entity listener side effects. This ensures transactions remain clean, transparent, and easy to debug.

### Tracking Lifecycle Diagram (Mermaid)

```mermaid
sequenceDiagram
    participant Client
    participant Controller as ProductPriceController
    participant Service as ProductPriceService
    participant HistoryService as PriceHistoryService
    participant PPRepo as ProductPriceRepository
    participant PHRepo as PriceHistoryRepository
    participant DB as PostgreSQL Database

    Client->>Controller: PUT /api/v1/prices/{id}
    Controller->>Service: updateProductPrice(id, dto)
    Note over Service: Fetch existing price & compare oldPrice vs newPrice
    Service->>PPRepo: save(entity)
    PPRepo->>DB: UPDATE product_prices
    
    alt Price has changed (oldPrice != newPrice)
        Service->>HistoryService: recordPriceHistory(product, seller, oldPrice, newPrice)
        Note over HistoryService: Calculate price difference & change percentage
        HistoryService->>PHRepo: save(historyEntity)
        PHRepo->>DB: INSERT INTO price_histories
    else Price is same
        Note over Service: Skip history creation
    end
    
    Note over Service: Transaction boundaries complete
    Service-->>Controller: Return ProductPriceResponseDTO
    Controller-->>Client: 200 OK
```

---

## 3. Backend Architecture Implementation

The implementation separates responsibilities using standard controller, service, repository, and DTO layouts.

### API Endpoints

1. **GET `/api/v1/price-history`**: Fetches all price histories globally with pagination.
2. **GET `/api/v1/products/{productId}/price-history`**: Fetches price histories for a specific product.
3. **GET `/api/v1/sellers/{sellerId}/price-history`**: Fetches price histories for a specific seller.

---

## 4. Future Analytics Dashboard Hookup

To support analytics dashboards, recommendation engines, and trend forecasting, `PriceHistoryService` includes the following highly optimized methods:

1. `getLargestPriceDrops(int limit)`: Retrieves the top `N` products with the most substantial price drops (`changePercentage < 0`, sorted ascending).
2. `getLargestPriceIncreases(int limit)`: Retrieves the top `N` products with the largest price increases (`changePercentage > 0`, sorted descending).
3. `getRecentPriceChanges(int limit)`: Retrieves the `N` most recent price changes across the catalog.

These methods utilize optimized native database queries with `JOIN FETCH` to prevent performance overhead.
