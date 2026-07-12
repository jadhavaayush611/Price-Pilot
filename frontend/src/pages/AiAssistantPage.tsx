import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Send, Trash2, Bot, User, Sparkles, RefreshCw, AlertCircle, ArrowRight, TrendingUp } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { apiService } from '../services/api';
import { getSavedCurrency, formatPrice } from '../currency';
import { useAuth } from '../context/AuthContext';

interface ProductCardDTO {
  id: string;
  name: string;
  brand: string;
  category: string;
  price: number;
  originalPrice: number;
  discount: number;
  sellersCount: number;
  imageUrl: string;
}

interface ComparisonDTO {
  products: ProductCardDTO[];
  summary: string;
}

interface BuyConfidenceDTO {
  score: number;
  reason: string;
}

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  products?: ProductCardDTO[];
  comparisons?: ComparisonDTO;
  buyConfidence?: BuyConfidenceDTO;
  suggestedPrompts?: string[];
  timestamp: Date;
}

export const AiAssistantPage: React.FC = () => {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [conversationId, setConversationId] = useState<string>('');
  
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const userCurrency = getSavedCurrency();

  // Redirect to login if unauthenticated
  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/login?from=/assistant');
      return;
    }
    // Generate new conversation ID on mount
    setConversationId(crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).substring(2));
  }, [isAuthenticated, navigate]);

  // Scroll to bottom on new messages
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, loading]);

  const handleSend = async (textToSend: string) => {
    if (!textToSend.trim() || loading) return;

    setError(null);
    const userMessageText = textToSend;
    setInput('');
    
    // Add user message to UI
    const userMessage: Message = {
      id: Math.random().toString(36).substring(2),
      role: 'user',
      content: userMessageText,
      timestamp: new Date()
    };
    
    setMessages(prev => [...prev, userMessage]);
    setLoading(true);

    try {
      const data = await apiService.assistantChat(userMessageText, conversationId);
      
      // Parse assistant response
      const assistantMessage: Message = {
        id: Math.random().toString(36).substring(2),
        role: 'assistant',
        content: data.response || "I couldn't process that query.",
        products: data.products,
        comparisons: data.comparisons,
        buyConfidence: data.buyConfidence,
        suggestedPrompts: data.suggestedPrompts || [],
        timestamp: new Date()
      };

      setMessages(prev => [...prev, assistantMessage]);
    } catch (err: any) {
      console.error('Error sending message:', err);
      setError(err?.response?.data?.detail || err.message || 'Failed to connect to the PricePilot AI Assistant.');
    } finally {
      setLoading(false);
    }
  };

  const handleClearHistory = async () => {
    if (!conversationId) return;
    try {
      await apiService.assistantClearMemory(conversationId);
    } catch (err) {
      console.error('Failed to clear backend memory:', err);
    }
    setMessages([]);
    setError(null);
    setConversationId(crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).substring(2));
  };

  // Pre-configured suggested prompts for the landing chat state
  const INITIAL_SUGGESTIONS = [
    { text: "Is now a good time to buy?", label: "Buy Confidence" },
    { text: "Which products are trending?", label: "Market Trends" },
    { text: "Recommend something similar to my saved products.", label: "For You" },
    { text: "Compare iPhone 15 Pro Max and Sony WH-1000XM5", label: "Product Comparison" }
  ];

  // Helper to format reasoning list item bullet points
  const renderFormattedMarkdown = (text: string) => {
    // Basic formatting for bullet points and bold text
    return text.split('\n').map((line, idx) => {
      if (line.startsWith('• ') || line.startsWith('- ')) {
        return (
          <li key={idx} className="ml-4 list-disc text-zinc-300 py-0.5">
            {renderLineWithBold(line.substring(2))}
          </li>
        );
      }
      return (
        <p key={idx} className="text-zinc-200 leading-relaxed mb-2 text-sm">
          {renderLineWithBold(line)}
        </p>
      );
    });
  };

  const renderLineWithBold = (line: string) => {
    const parts = line.split(/(\*\*.*?\*\*)/g);
    return parts.map((part, i) => {
      if (part.startsWith('**') && part.endsWith('**')) {
        return <strong key={i} className="text-white font-semibold">{part.slice(2, -2)}</strong>;
      }
      return part;
    });
  };

  // Helper to calculate confidence border colors
  const getConfidenceColor = (score: number) => {
    if (score >= 80) return 'text-emerald-400 border-emerald-500 bg-emerald-500/10';
    if (score >= 50) return 'text-amber-400 border-amber-500 bg-amber-500/10';
    return 'text-rose-400 border-rose-500 bg-rose-500/10';
  };

  return (
    <div className="max-w-4xl mx-auto flex flex-col h-[calc(100vh-12rem)] relative bg-[#09090b] border border-zinc-800 rounded-2xl overflow-hidden shadow-2xl">
      {/* Top Banner Header */}
      <div className="px-6 py-4 bg-zinc-950 border-b border-zinc-800 flex items-center justify-between z-10">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-xl bg-white text-black shadow-[0_0_15px_rgba(255,255,255,0.1)]">
            <Bot size={20} className="animate-pulse" />
          </div>
          <div>
            <h1 className="text-base font-bold text-white tracking-tight flex items-center gap-1.5">
              PricePilot Copilot <span className="text-[10px] tracking-wider uppercase bg-zinc-900 border border-zinc-800 px-1.5 py-0.5 rounded text-zinc-400 font-mono font-bold">RAG</span>
            </h1>
            <p className="text-xs text-zinc-400">Grounded in PricePilot database. No hallucinations.</p>
          </div>
        </div>
        
        {messages.length > 0 && (
          <button
            onClick={handleClearHistory}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-zinc-400 hover:text-rose-400 bg-zinc-900 hover:bg-rose-950/20 border border-zinc-800 hover:border-rose-900 rounded-lg active:scale-[0.98] transition-all cursor-pointer"
            title="Clear Chat History"
          >
            <Trash2 size={13} />
            <span>Clear History</span>
          </button>
        )}
      </div>

      {/* Main Conversation Box */}
      <div className="flex-1 overflow-y-auto p-6 space-y-6">
        {messages.length === 0 ? (
          /* Empty Chat Area with Welcome Info */
          <div className="h-full flex flex-col items-center justify-center text-center max-w-xl mx-auto space-y-6 py-8">
            <div className="p-4 rounded-3xl bg-zinc-900/60 border border-zinc-800 text-white shadow-inner flex items-center justify-center">
              <Sparkles size={36} className="text-zinc-400" />
            </div>
            <div>
              <h2 className="text-xl font-bold text-white">How can I assist your shopping today?</h2>
              <p className="text-sm text-zinc-400 mt-2">
                I retrieve real-time prices, historical averages, discounts, watchlists, and seller ratings from the PricePilot catalog to evaluate deal parameters for you.
              </p>
            </div>

            {/* Suggestions */}
            <div className="w-full grid grid-cols-1 sm:grid-cols-2 gap-3 mt-4">
              {INITIAL_SUGGESTIONS.map((item, index) => (
                <button
                  key={index}
                  onClick={() => handleSend(item.text)}
                  className="p-3.5 text-left text-xs bg-zinc-900/40 hover:bg-zinc-900 border border-zinc-800 hover:border-zinc-700 rounded-xl transition-all hover:scale-[1.01] flex flex-col justify-between h-20 active:scale-[0.99] group text-zinc-300 hover:text-white cursor-pointer"
                >
                  <span className="font-semibold text-zinc-500 uppercase tracking-widest text-[9px]">{item.label}</span>
                  <span className="line-clamp-2 mt-1">{item.text}</span>
                </button>
              ))}
            </div>
          </div>
        ) : (
          /* Render Active Messages */
          <div className="space-y-6">
            <AnimatePresence initial={false}>
              {messages.map((msg) => (
                <motion.div
                  key={msg.id}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.25 }}
                  className={`flex gap-4 ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
                >
                  {/* Avatar left side */}
                  {msg.role === 'assistant' && (
                    <div className="flex-shrink-0 h-8 w-8 rounded-lg bg-white text-black flex items-center justify-center font-bold text-xs shadow-md">
                      <Bot size={16} />
                    </div>
                  )}

                  <div className={`max-w-[85%] flex flex-col space-y-3 ${msg.role === 'user' ? 'items-end' : 'items-start'}`}>
                    {/* Message Bubble */}
                    <div className={`p-4 rounded-2xl text-sm border shadow-sm ${
                      msg.role === 'user'
                        ? 'bg-zinc-100 text-zinc-950 border-zinc-200'
                        : 'bg-zinc-900/50 text-zinc-200 border-zinc-800'
                    }`}>
                      {msg.role === 'user' ? (
                        <p className="whitespace-pre-line leading-relaxed">{msg.content}</p>
                      ) : (
                        <div className="space-y-1">
                          {renderFormattedMarkdown(msg.content)}
                        </div>
                      )}
                    </div>

                    {/* Buy Confidence Score Card */}
                    {msg.buyConfidence && (
                      <motion.div
                        initial={{ opacity: 0, scale: 0.95 }}
                        animate={{ opacity: 1, scale: 1 }}
                        className={`w-full max-w-sm rounded-xl p-4 border flex items-center gap-4 ${getConfidenceColor(msg.buyConfidence.score)}`}
                      >
                        <div className="relative flex-shrink-0 h-16 w-16 flex items-center justify-center rounded-full bg-zinc-950 border border-zinc-800">
                          <svg className="absolute inset-0 w-full h-full transform -rotate-90" viewBox="0 0 36 36">
                            <path
                              className="text-zinc-900"
                              strokeWidth="2.5"
                              stroke="currentColor"
                              fill="none"
                              d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                            />
                            <path
                              className={`${msg.buyConfidence.score >= 80 ? 'text-emerald-500' : msg.buyConfidence.score >= 50 ? 'text-amber-500' : 'text-rose-500'}`}
                              strokeWidth="2.5"
                              strokeDasharray={`${msg.buyConfidence.score}, 100`}
                              strokeLinecap="round"
                              stroke="currentColor"
                              fill="none"
                              d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                            />
                          </svg>
                          <span className="text-sm font-extrabold tracking-tight">{msg.buyConfidence.score}%</span>
                        </div>
                        <div className="flex-1">
                          <h4 className="text-xs font-bold uppercase tracking-wider text-zinc-400">Buy Confidence</h4>
                          <p className="text-xs font-medium text-zinc-200 mt-1 leading-relaxed">{msg.buyConfidence.reason}</p>
                        </div>
                      </motion.div>
                    )}

                    {/* Side-by-side Comparison Grid Table */}
                    {msg.comparisons && (
                      <motion.div
                        initial={{ opacity: 0, scale: 0.98 }}
                        animate={{ opacity: 1, scale: 1 }}
                        className="w-full bg-zinc-950 border border-zinc-800 rounded-xl overflow-hidden"
                      >
                        <div className="p-3 bg-zinc-900 border-b border-zinc-800">
                          <h4 className="text-xs font-bold text-white flex items-center gap-1.5">
                            <TrendingUp size={13} className="text-zinc-400" />
                            Side-by-Side Comparison Summary
                          </h4>
                        </div>
                        <div className="overflow-x-auto">
                          <table className="w-full text-left border-collapse text-xs">
                            <thead>
                              <tr className="border-b border-zinc-900 bg-zinc-900/30 text-zinc-400 font-semibold">
                                <th className="p-3">Metric</th>
                                {msg.comparisons.products.map((p, idx) => (
                                  <th key={idx} className="p-3 max-w-[120px] truncate">{p.name}</th>
                                ))}
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-zinc-900 text-zinc-300">
                              <tr>
                                <td className="p-3 font-medium text-zinc-400">Price</td>
                                {msg.comparisons.products.map((p, idx) => (
                                  <td key={idx} className="p-3 font-semibold text-white">{formatPrice(p.price, userCurrency)}</td>
                                ))}
                              </tr>
                              <tr>
                                <td className="p-3 font-medium text-zinc-400">Discount</td>
                                {msg.comparisons.products.map((p, idx) => (
                                  <td key={idx} className="p-3 text-emerald-400 font-semibold">{p.discount}% Off</td>
                                ))}
                              </tr>
                              <tr>
                                <td className="p-3 font-medium text-zinc-400">Brand</td>
                                {msg.comparisons.products.map((p, idx) => (
                                  <td key={idx} className="p-3">{p.brand}</td>
                                ))}
                              </tr>
                              <tr>
                                <td className="p-3 font-medium text-zinc-400">Sellers</td>
                                {msg.comparisons.products.map((p, idx) => (
                                  <td key={idx} className="p-3">{p.sellersCount} sellers</td>
                                ))}
                              </tr>
                            </tbody>
                          </table>
                        </div>
                        {msg.comparisons.summary && (
                          <div className="p-3 bg-zinc-900/40 border-t border-zinc-900 text-[11px] text-zinc-400 italic">
                            {msg.comparisons.summary}
                          </div>
                        )}
                      </motion.div>
                    )}

                    {/* Product Cards Row */}
                    {msg.products && msg.products.length > 0 && (
                      <div className="w-full grid grid-cols-1 sm:grid-cols-2 gap-3 mt-1">
                        {msg.products.map((p) => (
                          <div
                            key={p.id}
                            onClick={() => navigate(`/product/${p.id}`)}
                            className="bg-zinc-900 border border-zinc-800 hover:border-zinc-700 rounded-xl overflow-hidden p-3 flex gap-3 items-center hover:scale-[1.01] transition-all cursor-pointer group"
                          >
                            <div className="w-16 h-16 rounded-lg overflow-hidden bg-zinc-950 flex-shrink-0 border border-zinc-800 relative">
                              <img src={p.imageUrl} alt={p.name} className="w-full h-full object-cover object-center group-hover:scale-105 transition-transform" />
                              {p.discount > 0 && (
                                <span className="absolute top-0.5 left-0.5 bg-emerald-500 text-black text-[9px] font-extrabold px-1 py-0.2 rounded font-sans">
                                  {p.discount}% OFF
                                </span>
                              )}
                            </div>
                            <div className="flex-grow min-w-0">
                              <h4 className="text-xs font-bold text-white truncate">{p.name}</h4>
                              <p className="text-[10px] text-zinc-500 mt-0.5">{p.brand} · {p.category}</p>
                              
                              <div className="flex items-baseline gap-1.5 mt-1.5">
                                <span className="text-xs font-extrabold text-white">{formatPrice(p.price, userCurrency)}</span>
                                {p.originalPrice > p.price && (
                                  <span className="text-[10px] text-zinc-500 line-through">{formatPrice(p.originalPrice, userCurrency)}</span>
                                )}
                              </div>
                            </div>
                            <ArrowRight size={13} className="text-zinc-600 group-hover:text-white group-hover:translate-x-0.5 transition-all flex-shrink-0" />
                          </div>
                        ))}
                      </div>
                    )}

                    {/* Suggested Follow-up Prompt Pills */}
                    {msg.suggestedPrompts && msg.suggestedPrompts.length > 0 && !loading && (
                      <div className="flex flex-wrap gap-1.5 mt-2">
                        {msg.suggestedPrompts.map((pText, pIdx) => (
                          <button
                            key={pIdx}
                            onClick={() => handleSend(pText)}
                            className="px-2.5 py-1 text-[11px] font-medium text-zinc-400 hover:text-white bg-zinc-900 hover:bg-zinc-800 border border-zinc-800 rounded-full active:scale-[0.98] transition-all cursor-pointer"
                          >
                            {pText}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* Avatar right side for User */}
                  {msg.role === 'user' && (
                    <div className="flex-shrink-0 h-8 w-8 rounded-lg bg-zinc-800 text-zinc-100 flex items-center justify-center font-bold text-xs shadow-md border border-zinc-700">
                      <User size={16} />
                    </div>
                  )}
                </motion.div>
              ))}
            </AnimatePresence>
          </div>
        )}

        {/* Loading Indicator */}
        {loading && (
          <div className="flex gap-4 justify-start">
            <div className="flex-shrink-0 h-8 w-8 rounded-lg bg-white text-black flex items-center justify-center font-bold text-xs">
              <Bot size={16} />
            </div>
            <div className="space-y-3 w-full max-w-[80%]">
              <div className="p-4 rounded-2xl bg-zinc-900 border border-zinc-800 text-zinc-200">
                <div className="flex items-center gap-1.5 py-0.5 text-zinc-400">
                  <span className="w-1.5 h-1.5 bg-zinc-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                  <span className="w-1.5 h-1.5 bg-zinc-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                  <span className="w-1.5 h-1.5 bg-zinc-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                  <span className="text-xs font-mono text-zinc-500 ml-1.5">thinking over catalog context...</span>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Error State Banner */}
        {error && (
          <div className="p-4 bg-rose-950/20 border border-rose-900/50 rounded-xl text-rose-300 text-xs flex items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <AlertCircle size={15} />
              <span>{error}</span>
            </div>
            <button
              onClick={() => {
                const lastUserMsg = [...messages].reverse().find(m => m.role === 'user');
                if (lastUserMsg) {
                  // retry sending
                  handleSend(lastUserMsg.content);
                }
              }}
              className="flex items-center gap-1 px-2.5 py-1 bg-rose-950/60 border border-rose-800 hover:bg-rose-900/60 rounded-md hover:text-white transition-all cursor-pointer"
            >
              <RefreshCw size={11} className="animate-spin-slow" />
              <span>Retry</span>
            </button>
          </div>
        )}

        {/* scroll anchor */}
        <div ref={messagesEndRef} />
      </div>

      {/* Input box form */}
      <div className="p-4 bg-zinc-950 border-t border-zinc-800">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            handleSend(input);
          }}
          className="flex items-center gap-2 bg-zinc-900 border border-zinc-800 hover:border-zinc-700 focus-within:border-zinc-500 rounded-xl px-3 py-1.5 transition-all"
        >
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            disabled={loading}
            placeholder={loading ? "Thinking..." : "Ask a query (e.g. 'Has the iPhone 15 dropped in price?')"}
            className="flex-grow bg-transparent text-sm text-zinc-100 placeholder-zinc-500 focus:outline-none py-1.5"
          />
          <button
            type="submit"
            disabled={!input.trim() || loading}
            className={`p-2 rounded-lg transition-all ${
              input.trim() && !loading
                ? 'bg-white text-black hover:bg-zinc-200 active:scale-95 cursor-pointer'
                : 'text-zinc-500 bg-zinc-900 border border-zinc-800'
            }`}
          >
            <Send size={14} />
          </button>
        </form>
      </div>
    </div>
  );
};
