import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiService, apiClient } from './api';

describe('apiService Assistant Gateway Client', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('should call assistant chat endpoint and return structured response', async () => {
    const mockResponse = {
      data: {
        response: 'Test response content',
        conversationId: 'conv-abc-123',
        products: []
      }
    };

    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue(mockResponse);

    const result = await apiService.assistantChat('Hello Assistant', 'conv-abc-123');

    expect(postSpy).toHaveBeenCalledWith('/assistant/chat', {
      message: 'Hello Assistant',
      conversationId: 'conv-abc-123'
    });
    expect(result.response).toBe('Test response content');
    expect(result.conversationId).toBe('conv-abc-123');
  });

  it('should call assistant compare endpoint', async () => {
    const mockResponse = {
      data: {
        comparisons: {
          products: [],
          summary: 'Comparison summary'
        }
      }
    };

    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue(mockResponse);

    const result = await apiService.assistantCompare(['p1', 'p2'], 'conv-abc-123');

    expect(postSpy).toHaveBeenCalledWith('/assistant/compare', {
      productIds: ['p1', 'p2'],
      conversationId: 'conv-abc-123'
    });
    expect(result.comparisons.summary).toBe('Comparison summary');
  });

  it('should call assistant ask endpoint', async () => {
    const mockResponse = {
      data: {
        response: 'Direct answer'
      }
    };

    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue(mockResponse);

    const result = await apiService.assistantAsk('Is now a good time?', 'conv-abc-123');

    expect(postSpy).toHaveBeenCalledWith('/assistant/ask', {
      question: 'Is now a good time?',
      conversationId: 'conv-abc-123'
    });
    expect(result.response).toBe('Direct answer');
  });

  it('should call assistant clear memory endpoint', async () => {
    const mockResponse = {
      data: {
        status: 'success'
      }
    };

    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue(mockResponse);

    const result = await apiService.assistantClearMemory('conv-abc-123');

    expect(postSpy).toHaveBeenCalledWith('/assistant/clear_memory', {
      conversationId: 'conv-abc-123'
    });
    expect(result.status).toBe('success');
  });

  it('should list assistant conversations', async () => {
    const mockConversations = [{ id: 'c1', title: 'Chat 1', messages: [] }];
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: mockConversations });

    const result = await apiService.listAssistantConversations();

    expect(getSpy).toHaveBeenCalledWith('/assistant/conversations');
    expect(result).toHaveLength(1);
    expect(result[0].title).toBe('Chat 1');
  });

  it('should create an assistant conversation', async () => {
    const mockConv = { id: 'c2', title: 'New Thread', messages: [] };
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: mockConv });

    const result = await apiService.createAssistantConversation('New Thread');

    expect(postSpy).toHaveBeenCalledWith('/assistant/conversations', { title: 'New Thread' });
    expect(result.id).toBe('c2');
  });

  it('should send message to assistant conversation without active product', async () => {
    const mockResponse = {
      data: {
        conversationId: 'c2',
        messageId: 'm1',
        response: 'Grounded suggestion',
        intent: 'DISCOVERY',
        evidenceBundle: { groundedProducts: [], personalizationFactors: [], tradeOffs: [], unknownOrInsufficientDataNotes: [], suggestedActions: [] },
        suggestedPrompts: [],
        actions: []
      }
    };
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue(mockResponse);

    const result = await apiService.sendAssistantMessage('c2', 'Find headphones under $200');

    expect(postSpy).toHaveBeenCalledWith('/assistant/conversations/c2/messages', {
      content: 'Find headphones under $200',
      activeProductId: undefined
    });
    expect(result.response).toBe('Grounded suggestion');
    expect(result.intent).toBe('DISCOVERY');
  });

  it('should send message to assistant conversation WITH activeProductId', async () => {
    const mockResponse = {
      data: {
        conversationId: 'c2',
        messageId: 'm2',
        response: 'Product-specific analysis',
        intent: 'PRICE_ANALYSIS',
        evidenceBundle: { groundedProducts: [], personalizationFactors: [], tradeOffs: [], unknownOrInsufficientDataNotes: [], suggestedActions: [] },
        suggestedPrompts: [],
        actions: []
      }
    };
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue(mockResponse);

    const result = await apiService.sendAssistantMessage('c2', 'Is this a good time to buy?', 'prod-12345');

    expect(postSpy).toHaveBeenCalledWith('/assistant/conversations/c2/messages', {
      content: 'Is this a good time to buy?',
      activeProductId: 'prod-12345'
    });
    expect(result.response).toBe('Product-specific analysis');
    expect(result.intent).toBe('PRICE_ANALYSIS');
  });

  it('should verify sendAssistantMessage never includes userId parameter in request payload or url', async () => {
    const mockResponse = {
      data: {
        conversationId: 'c2',
        messageId: 'm3',
        response: 'Security verified',
        intent: 'DISCOVERY'
      }
    };
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue(mockResponse);

    await apiService.sendAssistantMessage('c2', 'Check security', 'prod-999');

    const [url, payload] = postSpy.mock.calls[0];
    expect(url).not.toContain('userId');
    expect(payload).not.toHaveProperty('userId');
    expect(payload).toHaveProperty('content', 'Check security');
    expect(payload).toHaveProperty('activeProductId', 'prod-999');
  });

  it('should delete assistant conversation', async () => {
    const deleteSpy = vi.spyOn(apiClient, 'delete').mockResolvedValue({ data: null });

    await apiService.deleteAssistantConversation('c2');

    expect(deleteSpy).toHaveBeenCalledWith('/assistant/conversations/c2');
  });

  it('should correctly receive and parse EXACT_MATCH classification in assistant response', async () => {
    const mockResponse = {
      data: {
        conversationId: 'c-exact',
        messageId: 'm-exact',
        response: 'Exact match found in the verified catalog:\n\n1. iPhone 15 — $799.00',
        intent: 'DISCOVERY',
        matchClassification: 'EXACT_MATCH',
        requestedEntity: 'iPhone 15',
        products: [
          { productId: 'p-1', productName: 'Apple iPhone 15', currentPrice: 799.0 }
        ],
        evidenceBundle: {
          groundedProducts: [{ productId: 'p-1', productName: 'Apple iPhone 15', currentPrice: 799.0 }],
          matchClassification: 'EXACT_MATCH',
          requestedEntity: 'iPhone 15'
        }
      }
    };
    vi.spyOn(apiClient, 'post').mockResolvedValue(mockResponse);

    const result = await apiService.sendAssistantMessage('c-exact', 'Find the iPhone 15');

    expect(result.matchClassification).toBe('EXACT_MATCH');
    expect(result.requestedEntity).toBe('iPhone 15');
    expect(result.response).toContain('Exact match found in the verified catalog:');
    expect(result.products).toHaveLength(1);
  });

  it('should correctly receive and parse CLOSE_MATCHES classification when exact product is absent', async () => {
    const mockResponse = {
      data: {
        conversationId: 'c-close',
        messageId: 'm-close',
        response: 'No exact iPhone 16 was found in the verified catalog.\n\nClosest available matches:\n1. iPhone 15 — $799.00\n2. iPhone 15 Pro — $999.00',
        intent: 'DISCOVERY',
        matchClassification: 'CLOSE_MATCHES',
        requestedEntity: 'iPhone 16',
        products: [
          { productId: 'p-1', productName: 'Apple iPhone 15', currentPrice: 799.0 },
          { productId: 'p-2', productName: 'Apple iPhone 15 Pro', currentPrice: 999.0 }
        ],
        evidenceBundle: {
          groundedProducts: [
            { productId: 'p-1', productName: 'Apple iPhone 15', currentPrice: 799.0 },
            { productId: 'p-2', productName: 'Apple iPhone 15 Pro', currentPrice: 999.0 }
          ],
          matchClassification: 'CLOSE_MATCHES',
          requestedEntity: 'iPhone 16'
        }
      }
    };
    vi.spyOn(apiClient, 'post').mockResolvedValue(mockResponse);

    const result = await apiService.sendAssistantMessage('c-close', 'Find me the iPhone 16');

    expect(result.matchClassification).toBe('CLOSE_MATCHES');
    expect(result.requestedEntity).toBe('iPhone 16');
    expect(result.response).toContain('No exact iPhone 16 was found in the verified catalog.');
    expect(result.response).toContain('Closest available matches:');
    expect(result.response).not.toContain('matching products');
  });

  it('should correctly receive and parse NO_MATCH classification', async () => {
    const mockResponse = {
      data: {
        conversationId: 'c-nomatch',
        messageId: 'm-nomatch',
        response: "I couldn't find an exact match or sufficiently close product in the verified catalog.",
        intent: 'DISCOVERY',
        matchClassification: 'NO_MATCH',
        requestedEntity: 'Electric Toothbrush',
        products: [],
        evidenceBundle: {
          groundedProducts: [],
          matchClassification: 'NO_MATCH',
          requestedEntity: 'Electric Toothbrush'
        }
      }
    };
    vi.spyOn(apiClient, 'post').mockResolvedValue(mockResponse);

    const result = await apiService.sendAssistantMessage('c-nomatch', 'Find electric toothbrush');

    expect(result.matchClassification).toBe('NO_MATCH');
    expect(result.response).toContain("I couldn't find an exact match or sufficiently close product in the verified catalog.");
    expect(result.products).toHaveLength(0);
  });
});
