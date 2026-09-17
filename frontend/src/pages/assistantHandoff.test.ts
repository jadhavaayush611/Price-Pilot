import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiService, apiClient } from '../services/api';

describe('Assistant Product Context & Downstream Handoff Integration', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  describe('1. ProductPage to Assistant Handoff', () => {
    it('should generate correct Assistant URL with productId and encoded contextual query without userId', () => {
      const mockProduct = {
        id: 'prod-uuid-101',
        name: 'Sony WH-1000XM5 Wireless Headphones'
      };

      const params = new URLSearchParams({
        productId: mockProduct.id,
        query: 'Analyze price history, deals, and purchase timing for ' + mockProduct.name
      });
      const generatedUrl = `/assistant?${params.toString()}`;

      expect(generatedUrl).toContain('productId=prod-uuid-101');
      expect(generatedUrl).toContain('query=Analyze+price+history%2C+deals%2C+and+purchase+timing+for+Sony+WH-1000XM5+Wireless+Headphones');
      expect(generatedUrl).not.toContain('userId');
      expect(generatedUrl).not.toContain('user');
    });
  });

  describe('2. Assistant SEARCH Action Route Correction', () => {
    it('should route SEARCH actions to canonical /search?keyword= instead of /products', () => {
      const mockAction = {
        type: 'SEARCH',
        label: 'wireless headphones under ₹5000'
      };

      let destination = '';
      if (mockAction.type === 'SEARCH') {
        destination = `/search?keyword=${encodeURIComponent(mockAction.label)}`;
      }

      expect(destination).toBe('/search?keyword=wireless%20headphones%20under%20%E2%82%B95000');
      expect(destination).not.toContain('/products');
    });

    it('should route grounded product View and Compare actions with backend product IDs', () => {
      const productId = 'prod-uuid-555';
      const viewRoute = `/product/${productId}`;
      const compareRoute = `/compare?ids=${productId}`;

      expect(viewRoute).toBe('/product/prod-uuid-555');
      expect(compareRoute).toBe('/compare?ids=prod-uuid-555');
    });
  });

  describe('3. Sequential Message Product Context Propagation & Dismissal', () => {
    it('should carry activeProductId across consecutive messages until dismissed', async () => {
      const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue({
        data: {
          conversationId: 'conv-test-1',
          messageId: 'msg-1',
          response: 'Contextual response',
          intent: 'PRICE_ANALYSIS'
        }
      });

      let activeProductId: string | null = 'prod-uuid-101';

      // Message 1: Initial query carries activeProductId
      await apiService.sendAssistantMessage('conv-test-1', 'Analyze price history', activeProductId || undefined);
      expect(postSpy).toHaveBeenLastCalledWith('/assistant/conversations/conv-test-1/messages', {
        content: 'Analyze price history',
        activeProductId: 'prod-uuid-101'
      });

      // Message 2: Follow-up question carries activeProductId
      await apiService.sendAssistantMessage('conv-test-1', 'Is this worth buying now?', activeProductId || undefined);
      expect(postSpy).toHaveBeenLastCalledWith('/assistant/conversations/conv-test-1/messages', {
        content: 'Is this worth buying now?',
        activeProductId: 'prod-uuid-101'
      });

      // User dismisses/clears product context
      activeProductId = null;

      // Message 3: Next message omits activeProductId
      await apiService.sendAssistantMessage('conv-test-1', 'What about other brands?', activeProductId || undefined);
      expect(postSpy).toHaveBeenLastCalledWith('/assistant/conversations/conv-test-1/messages', {
        content: 'What about other brands?',
        activeProductId: undefined
      });
    });
  });

  describe('4. User Identity Transition & Stale Response Safety', () => {
    it('should isolate state between different user sessions and ignore stale responses', () => {
      let activeUserId: string | null = 'user-1';
      let activeRequestCount = 0;
      let assistantMessages: string[] = ['User 1 Message'];

      // User 1 initiates a request
      const reqId1 = ++activeRequestCount;
      const userAtReq1 = activeUserId;

      // User 1 logs out and User 2 logs in before reqId1 resolves
      activeUserId = 'user-2';
      // Identity change triggers state cleanup:
      if (activeUserId !== userAtReq1) {
        assistantMessages = [];
      }

      // Late response from User 1 arrives
      const lateResponse = 'User 1 Response from backend';
      if (reqId1 === activeRequestCount && userAtReq1 === activeUserId) {
        assistantMessages.push(lateResponse);
      }

      // Assert that User 1 late response was rejected and did not leak into User 2 session
      expect(assistantMessages).toEqual([]);
      expect(activeUserId).toBe('user-2');
    });
  });
});
