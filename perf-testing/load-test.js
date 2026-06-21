import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '1m', target: 1000 },  // Ramp-up to 1000 VUs
    { duration: '3m', target: 1000 },  // Stay at 1000 VUs
    { duration: '1m', target: 5000 },  // Ramp-up to 5000 VUs
    { duration: '3m', target: 5000 },  // Stay at 5000 VUs
    { duration: '1m', target: 10000 }, // Ramp-up to 10000 VUs
    { duration: '3m', target: 10000 }, // Stay at 10000 VUs
    { duration: '2m', target: 0 },     // Ramp-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<200'], // 95% of requests must complete below 200ms
    http_req_failed: ['rate<0.01'],   // Error rate must be less than 1%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';

export default function () {
  // Scenario 1: Search Products (dynamic, GIN FTS search query)
  const searchQueries = ['laptop', 'phone', 'shoe', 'organic', 'wireless'];
  const randomKeyword = searchQueries[Math.floor(Math.random() * searchQueries.length)];
  const searchRes = http.get(`${BASE_URL}/search?keyword=${randomKeyword}`);
  check(searchRes, {
    'search status is 200': (r) => r.status === 200,
    'search response time ok': (r) => r.timings.duration < 300,
  });
  sleep(1);

  // Scenario 2: View Popular Products (cached)
  const popularRes = http.get(`${BASE_URL}/products/popular`);
  check(popularRes, {
    'popular status is 200': (r) => r.status === 200,
    'popular response time ok': (r) => r.timings.duration < 50, // Cached, should be extremely fast!
  });
  sleep(1);

  // Scenario 3: View Product Details (cached or database read)
  // Replaced with actual product UUIDs during execution
  const productIds = [
    'd3b07384-d113-4ec2-a5d9-480d8f74e645',
    'e5f07384-d113-4ec2-a5d9-480d8f74e646'
  ];
  const randomProductId = productIds[Math.floor(Math.random() * productIds.length)];
  const detailRes = http.get(`${BASE_URL}/products/${randomProductId}`);
  check(detailRes, {
    'details status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    'details response time ok': (r) => r.timings.duration < 100,
  });
  sleep(2);
}
