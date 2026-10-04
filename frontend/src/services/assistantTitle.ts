/**
 * Helper to derive concise, clean presentation titles from user queries for AI Assistant conversations.
 */

export function generateConversationTitle(query?: string | null): string {
  if (!query || !query.trim()) {
    return 'New Inquiry';
  }

  let text = query.trim();

  // Strip common query preambles
  const preambles = [
    /^(?:can you\s+)?(?:please\s+)?(?:find|search for|look for|show me|give me|recommend)\s+/i,
    /^(?:what is|what's|tell me about|how is)\s+/i,
    /^(?:i am looking for|i'm looking for|i want|looking for)\s+/i,
  ];

  for (const regex of preambles) {
    if (regex.test(text)) {
      text = text.replace(regex, '');
      break;
    }
  }

  // Handle specific pattern intents cleanly:
  // 1. "Is [Product] worth buying / a good deal / a good purchase / worth it?" -> "[Product] buying decision"
  const worthBuyingMatch = text.match(/^(?:is\s+(?:the\s+)?)(.+?)(?:\s+worth buying\??|\s+a good deal\??|\s+a good purchase\??|\s+worth it\??|\s+worth the money\??)$/i);
  if (worthBuyingMatch && worthBuyingMatch[1]) {
    text = `${worthBuyingMatch[1].trim()} buying decision`;
  }

  // 2. "Compare [X] and [Y]" -> "[X] vs [Y] comparison"
  const compareMatch = text.match(/^(?:compare\s+(?:between\s+)?)(.+)$/i);
  if (compareMatch && compareMatch[1]) {
    const items = compareMatch[1].replace(/\band\b/i, 'vs').trim();
    text = `${items} comparison`;
  }

  // 3. "Should I buy / When should I buy [X]?" -> "[X] purchase timing"
  const shouldBuyMatch = text.match(/^(?:(?:when\s+)?should i (?:buy|purchase)\s+(?:the\s+)?)(.+?)\??$/i);
  if (shouldBuyMatch && shouldBuyMatch[1]) {
    text = `${shouldBuyMatch[1].trim()} purchase timing`;
  }

  // Clean raw enum identifiers and internal markers
  text = text
    .replace(/\b(?:EXCELLENT_DEAL|GOOD_DEAL|FAIR_PRICE|POOR_DEAL|BUY_NOW|WAIT|STABLE|FALLING|RISING)\b/gi, '')
    .replace(/[_\t\n\r]+/g, ' ')
    .trim();

  if (!text) {
    return 'Shopping Inquiry';
  }

  // Capitalize the first letter
  text = text.charAt(0).toUpperCase() + text.slice(1);

  // Maximum character limit with clean word boundary truncation
  const MAX_LEN = 40;
  if (text.length > MAX_LEN) {
    const truncated = text.substring(0, 38);
    const lastSpace = truncated.lastIndexOf(' ');
    if (lastSpace > 15) {
      text = `${truncated.substring(0, lastSpace)}...`;
    } else {
      text = `${truncated.trim()}...`;
    }
  }

  return text;
}
