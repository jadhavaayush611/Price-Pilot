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
});
