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

  it('should send message to assistant conversation', async () => {
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

  it('should delete assistant conversation', async () => {
    const deleteSpy = vi.spyOn(apiClient, 'delete').mockResolvedValue({ data: null });

    await apiService.deleteAssistantConversation('c2');

    expect(deleteSpy).toHaveBeenCalledWith('/assistant/conversations/c2');
  });
});
