import { describe, it, expect, vi, beforeEach } from 'vitest';
import { parseValidUuids, UUID_REGEX } from './ComparisonPage';
import { apiService, apiClient } from '../services/api';

describe('Product Comparison Engine Unit & Contract Tests', () => {
  const uuid1 = 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d';
  const uuid2 = 'b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e';
  const uuid3 = 'c3d4e5f6-a7b8-9c0d-1e2f-3a4b5c6d7e8f';
  const uuid4 = 'd4e5f6a7-b8c9-0d1e-2f3a-4b5c6d7e8f9a';
  const uuid5 = 'e5f6a7b8-c9d0-1e2f-3a4b-5c6d7e8f9a0b';
  const uuid6 = 'f6a7b8c9-d0e1-2f3a-4b5c-6d7e8f9a0b1c';

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  describe('UUID Validation & URL Parsing', () => {
    it('validates authentic RFC4122 UUID format regex correctly', () => {
      expect(UUID_REGEX.test(uuid1)).toBe(true);
      expect(UUID_REGEX.test(uuid2)).toBe(true);
      expect(UUID_REGEX.test('p1')).toBe(false);
      expect(UUID_REGEX.test('p2')).toBe(false);
      expect(UUID_REGEX.test('product-1')).toBe(false);
      expect(UUID_REGEX.test('invalid-uuid-123')).toBe(false);
      expect(UUID_REGEX.test('')).toBe(false);
    });

    it('rejects fake placeholder IDs (p1, p2, demo) from comparison query string', () => {
      const rawParam = 'p1,p2,product-1,fake-id';
      const parsed = parseValidUuids(rawParam);
      expect(parsed).toEqual([]);
    });

    it('extracts exactly 2 real catalog product UUIDs', () => {
      const rawParam = `${uuid1},${uuid2}`;
      const parsed = parseValidUuids(rawParam);
      expect(parsed).toEqual([uuid1, uuid2]);
      expect(parsed.length).toBe(2);
    });

    it('extracts 3 real catalog product UUIDs', () => {
      const rawParam = `${uuid1},${uuid2},${uuid3}`;
      const parsed = parseValidUuids(rawParam);
      expect(parsed).toEqual([uuid1, uuid2, uuid3]);
      expect(parsed.length).toBe(3);
    });

    it('extracts 5 real catalog product UUIDs', () => {
      const rawParam = `${uuid1},${uuid2},${uuid3},${uuid4},${uuid5}`;
      const parsed = parseValidUuids(rawParam);
      expect(parsed).toEqual([uuid1, uuid2, uuid3, uuid4, uuid5]);
      expect(parsed.length).toBe(5);
    });

    it('caps and restricts selection to maximum 5 products if >5 provided', () => {
      const rawParam = `${uuid1},${uuid2},${uuid3},${uuid4},${uuid5},${uuid6}`;
      const parsed = parseValidUuids(rawParam);
      expect(parsed.length).toBe(5);
      expect(parsed).toEqual([uuid1, uuid2, uuid3, uuid4, uuid5]);
    });

    it('filters out malformed tokens and preserves only valid UUIDs', () => {
      const rawParam = `p1, ${uuid1} , invalid-id , ${uuid2} `;
      const parsed = parseValidUuids(rawParam);
      expect(parsed).toEqual([uuid1, uuid2]);
    });

    it('deduplicates identical product UUIDs', () => {
      const rawParam = `${uuid1},${uuid1},${uuid2},${uuid2}`;
      const parsed = parseValidUuids(rawParam);
      expect(parsed).toEqual([uuid1, uuid2]);
    });

    it('returns empty array when query string is empty or null', () => {
      expect(parseValidUuids('')).toEqual([]);
      expect(parseValidUuids(null)).toEqual([]);
      expect(parseValidUuids(undefined)).toEqual([]);
    });
  });

  describe('Comparison API Request Dispatching', () => {
    it('dispatches comparison request to backend with real UUIDs only', async () => {
      const mockMatrix: any = {
        comparisonId: 'matrix-123',
        products: [
          { id: uuid1, name: 'Product 1', brand: 'Brand 1', lowestPrice: 100 },
          { id: uuid2, name: 'Product 2', brand: 'Brand 2', lowestPrice: 200 },
        ],
        rows: [],
        scores: {},
        summary: 'Comparison summary',
        createdAt: new Date().toISOString(),
      };

      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: mockMatrix });

      const result = await apiService.getComparison([uuid1, uuid2]);

      expect(getSpy).toHaveBeenCalledWith('/compare', {
        params: { ids: `${uuid1},${uuid2}` },
      });
      expect(result.comparisonId).toBe('matrix-123');
      expect(result.products.length).toBe(2);
      expect(result.products[0].id).toBe(uuid1);
      expect(result.products[1].id).toBe(uuid2);
    });
  });
});
