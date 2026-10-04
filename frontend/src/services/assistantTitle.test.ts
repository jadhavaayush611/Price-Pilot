import { describe, it, expect } from 'vitest';
import { generateConversationTitle } from './assistantTitle.ts';

describe('Assistant Conversation Title Generator', () => {
  it('returns neutral default for empty or null query', () => {
    expect(generateConversationTitle(null)).toBe('New Inquiry');
    expect(generateConversationTitle('')).toBe('New Inquiry');
    expect(generateConversationTitle('   ')).toBe('New Inquiry');
  });

  it('cleans preamble and formats meaningful query title', () => {
    expect(generateConversationTitle('Find wireless headphones under ₹5,000'))
      .toBe('Wireless headphones under ₹5,000');
    expect(generateConversationTitle('Can you please find gaming laptops'))
      .toBe('Gaming laptops');
    expect(generateConversationTitle("I'm looking for 4K OLED monitors"))
      .toBe('4K OLED monitors');
  });

  it('formats buying decision questions concisely', () => {
    expect(generateConversationTitle('Is the Nothing Phone 2 worth buying?'))
      .toBe('Nothing Phone 2 buying decision');
    expect(generateConversationTitle('Is Sony WH-1000XM5 a good deal?'))
      .toBe('Sony WH-1000XM5 buying decision');
  });

  it('formats comparison queries cleanly', () => {
    expect(generateConversationTitle('Compare iPhone 15 Pro and Galaxy S24'))
      .toBe('IPhone 15 Pro vs Galaxy S24 comparison');
  });

  it('formats purchase timing queries', () => {
    expect(generateConversationTitle('Should I buy the MacBook Air M3?'))
      .toBe('MacBook Air M3 purchase timing');
  });

  it('truncates long user queries gracefully without breaking words awkwardly', () => {
    const longQuery = 'Looking for the best ultrabook laptop with 32GB RAM and 1TB SSD for software engineering and gaming';
    const title = generateConversationTitle(longQuery);
    expect(title.length).toBeLessThanOrEqual(42);
    expect(title.endsWith('...')).toBe(true);
  });

  it('strips internal enum keywords and underscores without raw enum leakage', () => {
    const title = generateConversationTitle('EXCELLENT_DEAL on Pixel 8a BUY_NOW');
    expect(title).not.toContain('EXCELLENT_DEAL');
    expect(title).not.toContain('BUY_NOW');
    expect(title).not.toContain('_');
    expect(title).toContain('Pixel 8a');
  });
});
