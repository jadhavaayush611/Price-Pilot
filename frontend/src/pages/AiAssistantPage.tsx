import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { 
  Send, Trash2, Bot, User, Sparkles, RefreshCw, AlertCircle, 
  Plus, MessageSquare, ShieldCheck, 
  Layers, CheckCircle2, AlertTriangle, ExternalLink, ChevronDown, ChevronUp, X
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { apiService } from '../services/api';
import { getSavedCurrency, formatPrice, getDisplayPrice, resolveAssistantDisplayPrice } from '../currency';
import { useAuth } from '../context/AuthContext';
import { ProductImage } from '../components/common/ProductImage';
import { generateConversationTitle } from '../services/assistantTitle.ts';
import type { 
  AssistantConversationDTO, 
  AssistantEvidenceBundle, 
  AssistantAction, 
  GroundedEvidenceItem,
  PersonalizationReasoningItem,
  TradeOffItem,
  ProductWithPrices
} from '../types';

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

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  intent?: string;
  evidenceBundle?: AssistantEvidenceBundle;
  products?: ProductCardDTO[];
  suggestedPrompts?: string[];
  actions?: AssistantAction[];
  timestamp: Date;
}

export const AiAssistantPage: React.FC = () => {
  const navigate = useNavigate();
  const { isAuthenticated, user } = useAuth();
  
  // Conversations State
  const [conversations, setConversations] = useState<AssistantConversationDTO[]>([]);
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null);
  const [conversationsLoading, setConversationsLoading] = useState(false);
  
  // Active Thread State
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [expandedEvidence, setExpandedEvidence] = useState<Record<string, boolean>>({});

  const [searchParams] = useSearchParams();
  const productIdParam = searchParams.get('productId');
  const queryParam = searchParams.get('query');

  // Active Product Context State
  const [activeProductId, setActiveProductId] = useState<string | null>(() => productIdParam || null);
  const [activeProduct, setActiveProduct] = useState<ProductWithPrices | null>(null);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const userCurrency = getSavedCurrency();

  // Race safety and user isolation tracking refs
  const initialQueryHandled = useRef(false);
  const activeUserIdRef = useRef<string | null>(user?.id || null);
  const activeRequestRef = useRef<number>(0);

  // Redirect to login if unauthenticated
  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/login?from=/assistant');
    }
  }, [isAuthenticated, navigate]);

  // Load active product metadata if productId is present in URL
  useEffect(() => {
    if (productIdParam) {
      setActiveProductId(productIdParam);
      apiService.getProduct(productIdParam)
        .then((prod) => {
          if (prod) {
            setActiveProduct(prod);
          }
        })
        .catch((err) => {
          console.warn('Could not load metadata for active product context:', err);
        });
    }
  }, [productIdParam]);

  // Handle user authentication transitions (login/logout/user switch)
  useEffect(() => {
    const currentUserId = user?.id || null;
    if (activeUserIdRef.current !== currentUserId) {
      activeUserIdRef.current = currentUserId;
      setMessages([]);
      setConversations([]);
      setActiveConversationId(null);
      setActiveProductId(null);
      setActiveProduct(null);
      setError(null);
      initialQueryHandled.current = false;
      if (isAuthenticated && currentUserId) {
        loadConversations();
      }
    }
  }, [user, isAuthenticated]);

  // Load conversations on mount
  useEffect(() => {
    if (isAuthenticated) {
      loadConversations();
    }
  }, [isAuthenticated]);

  // Auto-send query if passed via URL parameter (e.g. from Product Page or Dashboard)
  useEffect(() => {
    if (queryParam && !initialQueryHandled.current && isAuthenticated && !conversationsLoading) {
      initialQueryHandled.current = true;
      handleSend(queryParam);
    }
  }, [queryParam, isAuthenticated, conversationsLoading]);

  // Scroll to bottom on new messages
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, loading]);

  const loadConversations = async () => {
    try {
      setConversationsLoading(true);
      const list = await apiService.listAssistantConversations();
      setConversations(list);
      if (list.length > 0 && !activeConversationId) {
        selectConversation(list[0].id);
      }
    } catch (err) {
      console.warn('Failed to load conversations list, falling back to standalone session', err);
    } finally {
      setConversationsLoading(false);
    }
  };

  const selectConversation = async (convId: string) => {
    setActiveConversationId(convId);
    setError(null);
    try {
      setLoading(true);
      const conv = await apiService.getAssistantConversation(convId);
      if (conv && conv.messages) {
        const mapped: Message[] = conv.messages.map(m => ({
          id: m.id,
          role: m.role.toLowerCase() as 'user' | 'assistant',
          content: m.content,
          intent: m.intent,
          evidenceBundle: m.evidenceBundle,
          suggestedPrompts: m.evidenceBundle?.suggestedActions?.map(a => a.label) || [],
          actions: m.evidenceBundle?.suggestedActions || [],
          timestamp: new Date(m.createdAt)
        }));
        setMessages(mapped);
      }
    } catch (err) {
      console.error('Failed to load conversation details:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleCreateNewConversation = async () => {
    try {
      const newConv = await apiService.createAssistantConversation('New Inquiry');
      setConversations(prev => [{ ...newConv, title: 'New Inquiry' }, ...prev]);
      setActiveConversationId(newConv.id);
      setMessages([]);
      setError(null);
    } catch (err) {
      console.error('Failed to create new conversation:', err);
      setActiveConversationId(null);
      setMessages([]);
    }
  };

  const handleDeleteConversation = async (e: React.MouseEvent, convId: string) => {
    e.stopPropagation();
    try {
      await apiService.deleteAssistantConversation(convId);
      setConversations(prev => prev.filter(c => c.id !== convId));
      if (activeConversationId === convId) {
        const remaining = conversations.filter(c => c.id !== convId);
        if (remaining.length > 0) {
          selectConversation(remaining[0].id);
        } else {
          setActiveConversationId(null);
          setMessages([]);
        }
      }
    } catch (err) {
      console.error('Failed to delete conversation:', err);
    }
  };

  const toggleEvidence = (msgId: string) => {
    setExpandedEvidence(prev => ({
      ...prev,
      [msgId]: !prev[msgId]
    }));
  };

  const handleClearProductContext = () => {
    setActiveProductId(null);
    setActiveProduct(null);
  };

  const handleSend = async (textToSend: string) => {
    if (!textToSend.trim() || loading) return;

    setError(null);
    const userMessageText = textToSend;
    setInput('');
    
    const generateId = () => typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;

    const userMessage: Message = {
      id: generateId(),
      role: 'user',
      content: userMessageText,
      timestamp: new Date()
    };
    
    setMessages(prev => [...prev, userMessage]);
    setLoading(true);

    const currentReqId = ++activeRequestRef.current;
    const requestUserId = activeUserIdRef.current;

    try {
      let targetConvId = activeConversationId;
      const derivedTitle = generateConversationTitle(userMessageText);

      if (!targetConvId) {
        const newConv = await apiService.createAssistantConversation(derivedTitle);
        targetConvId = newConv.id;
        if (currentReqId === activeRequestRef.current && activeUserIdRef.current === requestUserId) {
          setActiveConversationId(newConv.id);
          setConversations(prev => [{ ...newConv, title: derivedTitle }, ...prev]);
        }
      } else {
        // If current thread has a generic initial title, update it with derived title from user's message
        setConversations(prev => prev.map(c => {
          if (c.id === targetConvId && (!c.title || c.title === 'New Inquiry' || c.title === 'New Shopping Inquiry' || c.title === 'Shopping Thread')) {
            return { ...c, title: derivedTitle };
          }
          return c;
        }));
      }

      // Send message carrying activeProductId if active
      const data = await apiService.sendAssistantMessage(
        targetConvId,
        userMessageText,
        activeProductId || undefined
      );

      // Stale response / user transition check
      if (currentReqId !== activeRequestRef.current || activeUserIdRef.current !== requestUserId) {
        return;
      }
      
      const assistantMessage: Message = {
        id: data.messageId || generateId(),
        role: 'assistant',
        content: data.response || "I couldn't process that query.",
        intent: data.intent,
        evidenceBundle: data.evidenceBundle,
        products: (data.products as unknown as ProductCardDTO[]) || (data.evidenceBundle?.groundedProducts as unknown as ProductCardDTO[]) || [],
        suggestedPrompts: data.suggestedPrompts || [],
        actions: data.actions || data.evidenceBundle?.suggestedActions || [],
        timestamp: new Date()
      };

      setMessages(prev => [...prev, assistantMessage]);
      
      if (data.evidenceBundle && (data.evidenceBundle.groundedProducts?.length > 0 || data.evidenceBundle.tradeOffs?.length > 0)) {
        setExpandedEvidence(prev => ({ ...prev, [assistantMessage.id]: true }));
      }
    } catch (err: unknown) {
      if (currentReqId !== activeRequestRef.current || activeUserIdRef.current !== requestUserId) {
        return;
      }
      console.error('Error sending message:', err);
      try {
        const legacyData = await apiService.assistantChat(userMessageText, activeConversationId || undefined);
        if (currentReqId !== activeRequestRef.current || activeUserIdRef.current !== requestUserId) {
          return;
        }
        const assistantMessage: Message = {
          id: generateId(),
          role: 'assistant',
          content: legacyData.response || "I couldn't process that query.",
          products: legacyData.products,
          suggestedPrompts: legacyData.suggestedPrompts || [],
          timestamp: new Date()
        };
        setMessages(prev => [...prev, assistantMessage]);
      } catch (legacyErr: unknown) {
        if (currentReqId !== activeRequestRef.current || activeUserIdRef.current !== requestUserId) {
          return;
        }
        const errorObj = legacyErr as { response?: { data?: { detail?: string } }; message?: string };
        setError(errorObj?.response?.data?.detail || errorObj.message || 'Failed to connect to the PricePilot Shopping Assistant.');
      }
    } finally {
      if (currentReqId === activeRequestRef.current && activeUserIdRef.current === requestUserId) {
        setLoading(false);
      }
    }
  };

  const INITIAL_SUGGESTIONS = [
    { text: "Find top-rated laptops for coding and gaming", label: "Product Discovery" },
    { text: "Compare iPhone 15 Pro Max and Samsung Galaxy S24 Ultra", label: "Product Comparison" },
    { text: "What products should I buy based on my saved preferences?", label: "Personalized Advice" },
    { text: "Is now a good time to buy, or should I wait for a price drop?", label: "Purchase Timing" },
    { text: "What are my current shopping preferences and budget limits?", label: "Preferences" },
    { text: "Set an alert when Sony WH-1000XM5 drops in price", label: "Watchlist Alerts" }
  ];

  const getDealBadgeClass = (dealQuality?: string) => {
    if (!dealQuality) return 'bg-zinc-800 text-zinc-400 border-zinc-700/60';
    const q = dealQuality.toUpperCase();
    switch (q) {
      case 'EXCELLENT_DEAL':
      case 'EXCELLENT':
        return 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30';
      case 'GOOD_DEAL':
      case 'GOOD':
        return 'bg-blue-500/15 text-blue-400 border-blue-500/30';
      case 'FAIR_PRICE':
      case 'FAIR':
        return 'bg-amber-500/15 text-amber-400 border-amber-500/30';
      case 'POOR_DEAL':
      case 'POOR':
      case 'OVERPRICED':
        return 'bg-rose-500/15 text-rose-400 border-rose-500/30';
      case 'AVAILABLE':
        return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20';
      default:
        return 'bg-zinc-800 text-zinc-400 border-zinc-700/60';
    }
  };

  const formatDealQuality = (quality?: string): string => {
    if (!quality) return 'Verified Price';
    const q = quality.toUpperCase();
    switch (q) {
      case 'EXCELLENT_DEAL':
      case 'EXCELLENT':
        return 'Excellent Deal';
      case 'GOOD_DEAL':
      case 'GOOD':
        return 'Good Deal';
      case 'FAIR_PRICE':
      case 'FAIR':
        return 'Fair Price';
      case 'POOR_DEAL':
      case 'POOR':
        return 'Poor Deal';
      case 'OVERPRICED':
        return 'Overpriced';
      case 'AVAILABLE':
        return 'Verified Price';
      case 'UNKNOWN':
        return 'Catalog Price';
      default:
        return quality.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
    }
  };

  const formatPurchaseSignal = (signal?: string): string => {
    if (!signal) return '';
    const s = signal.toUpperCase();
    switch (s) {
      case 'BUY_NOW':
        return 'Buy Now';
      case 'GOOD_TIME':
        return 'Good Time to Buy';
      case 'WAIT':
        return 'Wait for Drop';
      case 'NEUTRAL':
        return 'Neutral Timing';
      case 'UNKNOWN':
        return '';
      default:
        return signal.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
    }
  };

  const getSignalTextColor = (signal?: string): string => {
    if (!signal) return 'text-zinc-300';
    const s = signal.toUpperCase();
    switch (s) {
      case 'BUY_NOW':
        return 'text-emerald-400 font-semibold';
      case 'GOOD_TIME':
        return 'text-emerald-300 font-medium';
      case 'WAIT':
        return 'text-amber-400 font-medium';
      case 'NEUTRAL':
        return 'text-zinc-300';
      default:
        return 'text-zinc-300';
    }
  };

  const formatPriceTrend = (trend?: string): string => {
    if (!trend) return '';
    const t = trend.toUpperCase();
    switch (t) {
      case 'STABLE':
        return 'Stable Price';
      case 'FALLING':
        return 'Falling Price';
      case 'RISING':
        return 'Rising Price';
      case 'VOLATILE':
        return 'Volatile Price';
      default:
        return trend.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
    }
  };

  const cleanRawEnums = (text: string): string => {
    return text
      .replace(/\bEXCELLENT_DEAL\b/g, 'Excellent Deal')
      .replace(/\bGOOD_DEAL\b/g, 'Good Deal')
      .replace(/\bFAIR_PRICE\b/g, 'Fair Price')
      .replace(/\bPOOR_DEAL\b/g, 'Poor Deal')
      .replace(/\bOVERPRICED\b/g, 'Overpriced')
      .replace(/\bBUY_NOW\b/g, 'Buy Now')
      .replace(/\bGOOD_TIME\b/g, 'Good Time to Buy')
      .replace(/\bWAIT_FOR_DROP\b/g, 'Wait for Drop');
  };

  const renderFormattedMarkdown = (text: string) => {
    const dealRatingRegex = /^(?:[-•*]\s*)?Deal Rating:\s*([A-Za-z0-9_]+)(?:\s*\((?:Signal:\s*([A-Za-z0-9_]+))?(?:,?\s*Trend:\s*([A-Za-z0-9_]+))?\))?/i;

    return text.split('\n').map((line, idx) => {
      const trimmed = line.trim();
      if (!trimmed) {
        return <div key={idx} className="h-1" />;
      }

      // Check for Deal Rating / Signal / Trend summary line
      const dealMatch = trimmed.match(dealRatingRegex);
      if (dealMatch) {
        const dealQualityRaw = dealMatch[1];
        const signalRaw = dealMatch[2];
        const trendRaw = dealMatch[3];

        return (
          <div 
            key={idx} 
            className="my-2.5 p-3 rounded-xl bg-zinc-950/80 border border-zinc-800/90 shadow-sm flex flex-wrap items-center justify-between gap-2.5"
          >
            <div className="flex items-center gap-2">
              <span className={`px-2.5 py-1 rounded-lg text-xs font-bold border ${getDealBadgeClass(dealQualityRaw)}`}>
                {formatDealQuality(dealQualityRaw)}
              </span>
              {signalRaw && signalRaw !== 'UNKNOWN' && (
                <div className="flex items-center gap-1.5 text-xs">
                  <span className="text-zinc-500 font-medium">Decision:</span>
                  <span className={getSignalTextColor(signalRaw)}>
                    {formatPurchaseSignal(signalRaw)}
                  </span>
                </div>
              )}
            </div>
            {trendRaw && trendRaw !== 'UNKNOWN' && (
              <div className="flex items-center gap-1.5 text-xs text-zinc-400">
                <span className="text-zinc-500 font-medium">Trend:</span>
                <span className="font-medium text-zinc-300">
                  {formatPriceTrend(trendRaw)}
                </span>
              </div>
            )}
          </div>
        );
      }

      if (line.startsWith('### ')) {
        return <h3 key={idx} className="text-sm font-bold text-white mt-2 mb-1">{cleanRawEnums(line.substring(4))}</h3>;
      }
      if (line.startsWith('## ')) {
        return <h2 key={idx} className="text-base font-bold text-white mt-3 mb-1.5">{cleanRawEnums(line.substring(3))}</h2>;
      }
      if (line.startsWith('• ') || line.startsWith('- ') || line.startsWith('* ')) {
        return (
          <li key={idx} className="ml-4 list-disc text-zinc-300 py-0.5 text-xs">
            {renderLineWithBold(cleanRawEnums(line.substring(2)))}
          </li>
        );
      }
      return (
        <p key={idx} className="text-zinc-200 leading-relaxed mb-2 text-xs sm:text-sm">
          {renderLineWithBold(cleanRawEnums(line))}
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


  return (
    <div className="max-w-6xl mx-auto flex h-[calc(100vh-10rem)] bg-[#09090b] border border-zinc-800 rounded-2xl overflow-hidden shadow-2xl">
      {/* Left Sidebar: Conversations Management */}
      <div className="w-64 bg-zinc-950/80 border-r border-zinc-800 hidden md:flex flex-col">
        <div className="p-3 border-b border-zinc-800">
          <button
            onClick={handleCreateNewConversation}
            className="w-full flex items-center justify-center gap-2 py-2 px-3 bg-zinc-900 hover:bg-zinc-800 text-white text-xs font-semibold rounded-xl border border-zinc-700/50 hover:border-zinc-600 transition-all cursor-pointer active:scale-[0.98]"
          >
            <Plus size={14} />
            <span>New Chat</span>
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-2 space-y-1">
          {conversationsLoading && (
            <div className="p-3 text-center text-xs text-zinc-500">Loading chats...</div>
          )}
          {!conversationsLoading && conversations.length === 0 && (
            <div className="p-4 text-center text-xs text-zinc-600">
              No previous conversations. Start a new shopping chat!
            </div>
          )}
          {conversations.map((conv) => {
            const isActive = activeConversationId === conv.id;
            return (
              <div
                key={conv.id}
                onClick={() => selectConversation(conv.id)}
                className={`group flex items-center justify-between p-2.5 rounded-xl text-xs cursor-pointer transition-all ${
                  isActive 
                    ? 'bg-zinc-900 text-white border border-zinc-700/60 shadow-sm' 
                    : 'text-zinc-400 hover:bg-zinc-900/50 hover:text-zinc-200'
                }`}
              >
                <div className="flex items-center gap-2 min-w-0">
                  <MessageSquare size={13} className={isActive ? 'text-white' : 'text-zinc-500'} />
                  <span className="truncate font-medium">{generateConversationTitle(conv.title)}</span>
                </div>
                <button
                  onClick={(e) => handleDeleteConversation(e, conv.id)}
                  className="opacity-0 group-hover:opacity-100 p-1 text-zinc-500 hover:text-rose-400 transition-opacity"
                  title="Delete chat"
                >
                  <Trash2 size={12} />
                </button>
              </div>
            );
          })}
        </div>

        <div className="p-3 border-t border-zinc-800/80 bg-zinc-950 text-[10px] text-zinc-500 flex items-center gap-1.5">
          <ShieldCheck size={12} className="text-emerald-400" />
          <span>Verified catalog data · Clear shopping decisions</span>
        </div>
      </div>

      {/* Main Conversation Column */}
      <div className="flex-1 flex flex-col h-full bg-[#09090b] relative">
        {/* Top Header */}
        <div className="px-6 py-3.5 bg-zinc-950 border-b border-zinc-800 flex items-center justify-between z-10">
          <div className="flex items-center gap-3 min-w-0">
            <div className="p-2 rounded-xl bg-white text-black shadow-[0_0_15px_rgba(255,255,255,0.1)] shrink-0">
              <Bot size={18} className="animate-pulse" />
            </div>
            <div className="min-w-0">
              <h1 className="text-sm font-bold text-white tracking-tight truncate max-w-[200px] sm:max-w-md">
                {(() => {
                  const activeConv = conversations.find(c => c.id === activeConversationId);
                  return activeConv 
                    ? generateConversationTitle(activeConv.title) 
                    : (messages.length > 0 && messages[0].role === 'user' ? generateConversationTitle(messages[0].content) : 'Shopping Assistant');
                })()}
              </h1>
              <p className="text-[11px] text-zinc-400 truncate">Ask questions, compare products, and evaluate purchase timing</p>
            </div>
          </div>
          
          <div className="flex items-center gap-2">
            <button
              onClick={handleCreateNewConversation}
              className="md:hidden flex items-center gap-1 px-2.5 py-1 text-xs text-zinc-300 bg-zinc-900 hover:bg-zinc-800 border border-zinc-800 rounded-lg"
            >
              <Plus size={12} />
              <span>New</span>
            </button>
            {messages.length > 0 && (
              <button
                onClick={() => setMessages([])}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-zinc-400 hover:text-rose-400 bg-zinc-900 hover:bg-rose-950/20 border border-zinc-800 hover:border-rose-900 rounded-lg active:scale-[0.98] transition-all cursor-pointer"
                title="Clear View"
              >
                <Trash2 size={12} />
                <span className="hidden sm:inline">Clear View</span>
              </button>
            )}
          </div>
        </div>

        {/* Message Stream */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {messages.length === 0 ? (
            /* Welcome Empty State */
            <div className="h-full flex flex-col items-center justify-center text-center max-w-xl mx-auto space-y-6 py-8">
              <div className="p-4 rounded-3xl bg-zinc-900/60 border border-zinc-800 text-white shadow-inner flex items-center justify-center">
                <Sparkles size={32} className="text-zinc-300" />
              </div>
              <div>
                <h2 className="text-lg font-bold text-white">How can I assist your shopping decision today?</h2>
                <p className="text-xs text-zinc-400 mt-2 leading-relaxed">
                  I orchestrate verified catalog facts, seller ratings, price analytics, discount history, and your personalized preferences.
                </p>
              </div>

              {/* Suggestions Grid */}
              <div className="w-full grid grid-cols-1 sm:grid-cols-2 gap-2.5 mt-2">
                {INITIAL_SUGGESTIONS.map((item, index) => (
                  <button
                    key={index}
                    onClick={() => handleSend(item.text)}
                    className="p-3 text-left text-xs bg-zinc-900/40 hover:bg-zinc-900 border border-zinc-800 hover:border-zinc-700 rounded-xl transition-all hover:scale-[1.01] flex flex-col justify-between h-18 active:scale-[0.99] group text-zinc-300 hover:text-white cursor-pointer"
                  >
                    <span className="font-semibold text-zinc-500 uppercase tracking-widest text-[9px]">{item.label}</span>
                    <span className="line-clamp-2 mt-1">{item.text}</span>
                  </button>
                ))}
              </div>
            </div>
          ) : (
            /* Active Messages List */
            <div className="space-y-6">
              <AnimatePresence initial={false}>
                {messages.map((msg) => {
                  const isAssistant = msg.role === 'assistant';
                  const bundle = msg.evidenceBundle;
                  const isExpanded = expandedEvidence[msg.id] ?? false;

                  return (
                    <motion.div
                      key={msg.id}
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ duration: 0.25 }}
                      className={`flex gap-3.5 ${isAssistant ? 'justify-start' : 'justify-end'}`}
                    >
                      {isAssistant && (
                        <div className="flex-shrink-0 h-8 w-8 rounded-lg bg-white text-black flex items-center justify-center font-bold text-xs shadow-md">
                          <Bot size={15} />
                        </div>
                      )}

                      <div className={`max-w-[90%] sm:max-w-[85%] flex flex-col space-y-3 ${isAssistant ? 'items-start' : 'items-end'}`}>
                        {/* Message Content Bubble */}
                        <div className={`p-4 rounded-2xl text-xs sm:text-sm border shadow-sm ${
                          isAssistant
                            ? 'bg-zinc-900/70 text-zinc-200 border-zinc-800'
                            : 'bg-zinc-100 text-zinc-950 border-zinc-200'
                        }`}>
                          {isAssistant ? (
                            <div>
                              {renderFormattedMarkdown(msg.content)}
                            </div>
                          ) : (
                            <p className="whitespace-pre-line leading-relaxed">{msg.content}</p>
                          )}
                        </div>

                        {/* Grounded Evidence Bundle Drawer */}
                        {isAssistant && bundle && (
                          <div className="w-full bg-zinc-950/90 border border-zinc-800/80 rounded-xl overflow-hidden mt-1">
                            {/* Evidence Drawer Toggle Header */}
                            <button
                              onClick={() => toggleEvidence(msg.id)}
                              className="w-full p-2.5 px-3 bg-zinc-900/60 hover:bg-zinc-900 flex items-center justify-between text-xs text-zinc-300 font-semibold border-b border-zinc-800/60 transition-all cursor-pointer"
                            >
                              <div className="flex items-center gap-2">
                                <Layers size={13} className="text-zinc-400" />
                                <span>Verified Evidence & Decision Context</span>
                                {bundle.groundedProducts?.length > 0 && (
                                  <span className="text-[10px] bg-zinc-800 px-1.5 py-0.2 rounded text-zinc-400 font-mono">
                                    {bundle.groundedProducts.length} items
                                  </span>
                                )}
                              </div>
                              {isExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                            </button>

                            {/* Collapsible Evidence Content */}
                            {isExpanded && (
                              <div className="p-3 space-y-3.5 text-xs">
                                {/* 1. Verified Catalog Products Cards */}
                                {bundle.groundedProducts && bundle.groundedProducts.length > 0 && (
                                  <div>
                                    <h4 className="text-[10px] font-bold uppercase tracking-wider text-zinc-500 mb-2">
                                      Verified Product Details
                                    </h4>
                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                                      {bundle.groundedProducts.map((p: GroundedEvidenceItem) => {
                                        const displayCurrentPrice = resolveAssistantDisplayPrice(p.currentPrice, p.currency, userCurrency);
                                        const displayOrigPrice = p.originalPrice ? resolveAssistantDisplayPrice(p.originalPrice, p.currency, userCurrency) : undefined;

                                        return (
                                          <div
                                            key={p.productId}
                                            className="p-2.5 bg-zinc-900/80 border border-zinc-800 rounded-lg flex flex-col justify-between space-y-2 hover:border-zinc-700 transition-all"
                                          >
                                            <div>
                                              <div className="flex items-center justify-between gap-1">
                                                <span className="font-bold text-white truncate">{p.productName}</span>
                                                {p.dealQuality && (
                                                  <span className={`text-[9px] font-semibold px-1.5 py-0.5 rounded border ${getDealBadgeClass(p.dealQuality)}`}>
                                                    {formatDealQuality(p.dealQuality)}
                                                  </span>
                                                )}
                                              </div>
                                              <div className="text-[10px] text-zinc-400 mt-0.5">
                                                {p.brand} {p.category && `· ${p.category}`}
                                              </div>
                                              <div className="flex items-baseline gap-2 mt-1.5">
                                                <span className="font-bold text-white text-sm">
                                                  {formatPrice(displayCurrentPrice, userCurrency)}
                                                </span>
                                                {displayOrigPrice && displayOrigPrice > displayCurrentPrice && (
                                                  <span className="text-[10px] text-zinc-500 line-through">
                                                    {formatPrice(displayOrigPrice, userCurrency)}
                                                  </span>
                                                )}
                                                {p.discountPercentage != null && p.discountPercentage > 0 && (
                                                  <span className="text-[10px] font-semibold text-emerald-400">
                                                    {p.discountPercentage.toFixed(0)}% off
                                                  </span>
                                                )}
                                              </div>
                                            </div>

                                          {/* Action Buttons */}
                                          <div className="flex items-center gap-1.5 pt-1 border-t border-zinc-800/60">
                                            <button
                                              onClick={() => navigate(`/product/${p.productId}`)}
                                              className="flex-1 py-1 px-2 text-[10px] font-medium bg-zinc-800 hover:bg-zinc-700 text-zinc-200 rounded text-center transition-all cursor-pointer"
                                            >
                                              View
                                            </button>
                                            <button
                                              onClick={() => navigate(`/compare?ids=${p.productId}`)}
                                              className="flex-1 py-1 px-2 text-[10px] font-medium bg-zinc-800 hover:bg-zinc-700 text-zinc-200 rounded text-center transition-all cursor-pointer"
                                            >
                                              Compare
                                            </button>
                                          </div>
                                        </div>
                                      );
                                    })}
                                    </div>
                                  </div>
                                )}

                                {/* 2. Personalization Reasoning */}
                                {bundle.personalizationFactors && bundle.personalizationFactors.length > 0 && (
                                  <div>
                                    <h4 className="text-[10px] font-bold uppercase tracking-wider text-zinc-500 mb-1.5">
                                      Personalization Factors
                                    </h4>
                                    <div className="space-y-1.5">
                                      {bundle.personalizationFactors.map((f: PersonalizationReasoningItem, fIdx: number) => (
                                        <div key={fIdx} className="p-2 bg-zinc-900/50 border border-zinc-800/60 rounded flex items-center justify-between text-[11px]">
                                          <div className="flex items-center gap-2">
                                            <CheckCircle2 size={12} className="text-emerald-400 flex-shrink-0" />
                                            <span className="font-semibold text-zinc-300">{f.factor}:</span>
                                            <span className="text-zinc-400">{f.explanation}</span>
                                          </div>
                                          <span className="text-[10px] text-zinc-500 font-mono">
                                            {(f.confidenceScore * 100).toFixed(0)}%
                                          </span>
                                        </div>
                                      ))}
                                    </div>
                                  </div>
                                )}

                                {/* 3. Trade-Off Analysis */}
                                {bundle.tradeOffs && bundle.tradeOffs.length > 0 && (
                                  <div>
                                    <h4 className="text-[10px] font-bold uppercase tracking-wider text-zinc-500 mb-1.5">
                                      Evaluated Trade-Offs
                                    </h4>
                                    <div className="space-y-1.5">
                                      {bundle.tradeOffs.map((t: TradeOffItem, tIdx: number) => (
                                        <div key={tIdx} className="p-2 bg-zinc-900/50 border border-zinc-800/60 rounded text-[11px] grid grid-cols-3 gap-2">
                                          <span className="font-semibold text-zinc-300">{t.dimension}</span>
                                          <span className="text-emerald-400">✓ {t.pro}</span>
                                          <span className="text-rose-400">✗ {t.con}</span>
                                        </div>
                                      ))}
                                    </div>
                                  </div>
                                )}

                                {/* 4. Honest Data Limits Callout */}
                                {bundle.unknownOrInsufficientDataNotes && bundle.unknownOrInsufficientDataNotes.length > 0 && (
                                  <div className="p-2.5 bg-amber-950/20 border border-amber-900/40 rounded text-amber-300/90 text-[11px] flex items-start gap-2">
                                    <AlertTriangle size={13} className="text-amber-400 flex-shrink-0 mt-0.5" />
                                    <div>
                                      <span className="font-semibold">Data Limitations: </span>
                                      {bundle.unknownOrInsufficientDataNotes.join('; ')}
                                    </div>
                                  </div>
                                )}
                              </div>
                            )}
                          </div>
                        )}

                        {/* Interactive Suggested Actions */}
                        {isAssistant && msg.actions && msg.actions.length > 0 && (
                          <div className="flex flex-wrap gap-1.5 mt-1">
                            {msg.actions.map((act, aIdx) => (
                              <button
                                key={aIdx}
                                onClick={() => {
                                  if (act.actionUrl) {
                                    navigate(act.actionUrl);
                                  } else if (act.type === 'SEARCH') {
                                    navigate(`/search?keyword=${encodeURIComponent(act.label)}`);
                                  } else {
                                    handleSend(act.label);
                                  }
                                }}
                                className="px-2.5 py-1 text-[11px] font-medium text-zinc-300 hover:text-white bg-zinc-900 hover:bg-zinc-800 border border-zinc-700/60 rounded-lg transition-all cursor-pointer flex items-center gap-1 active:scale-[0.98]"
                              >
                                <span>{act.label}</span>
                                {act.actionUrl && <ExternalLink size={10} className="text-zinc-500" />}
                              </button>
                            ))}
                          </div>
                        )}

                        {/* Suggested Follow-up Prompts */}
                        {isAssistant && msg.suggestedPrompts && msg.suggestedPrompts.length > 0 && !loading && (
                          <div className="flex flex-wrap gap-1.5 mt-1">
                            {msg.suggestedPrompts
                              .filter(pText => !msg.actions?.some(a => a.label?.toLowerCase() === pText?.toLowerCase()))
                              .map((pText, pIdx) => (
                              <button
                                key={pIdx}
                                onClick={() => handleSend(pText)}
                                className="px-2.5 py-1 text-[10px] font-medium text-zinc-400 hover:text-white bg-zinc-900/60 hover:bg-zinc-800 border border-zinc-800 rounded-full active:scale-[0.98] transition-all cursor-pointer"
                              >
                                {pText}
                              </button>
                            ))}
                          </div>
                        )}
                      </div>

                      {!isAssistant && (
                        <div className="flex-shrink-0 h-8 w-8 rounded-lg bg-zinc-800 text-zinc-100 flex items-center justify-center font-bold text-xs shadow-md border border-zinc-700">
                          <User size={15} />
                        </div>
                      )}
                    </motion.div>
                  );
                })}
              </AnimatePresence>
            </div>
          )}

          {/* Thinking Indicator */}
          {loading && (
            <div className="flex gap-3.5 justify-start">
              <div className="flex-shrink-0 h-8 w-8 rounded-lg bg-white text-black flex items-center justify-center font-bold text-xs">
                <Bot size={15} />
              </div>
              <div className="p-3.5 rounded-2xl bg-zinc-900 border border-zinc-800 text-zinc-200">
                <div className="flex items-center gap-1.5 text-zinc-400">
                  <span className="w-1.5 h-1.5 bg-zinc-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                  <span className="w-1.5 h-1.5 bg-zinc-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                  <span className="w-1.5 h-1.5 bg-zinc-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                  <span className="text-xs font-mono text-zinc-500 ml-2">Retrieving facts & evaluating decision evidence...</span>
                </div>
              </div>
            </div>
          )}

          {/* Error Banner */}
          {error && (
            <div className="p-3.5 bg-rose-950/20 border border-rose-900/50 rounded-xl text-rose-300 text-xs flex items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <AlertCircle size={15} />
                <span>{error}</span>
              </div>
              <button
                onClick={() => {
                  const lastUserMsg = [...messages].reverse().find(m => m.role === 'user');
                  if (lastUserMsg) {
                    handleSend(lastUserMsg.content);
                  }
                }}
                className="flex items-center gap-1 px-2.5 py-1 bg-rose-950/60 border border-rose-800 hover:bg-rose-900/60 rounded-md hover:text-white transition-all cursor-pointer"
              >
                <RefreshCw size={11} />
                <span>Retry</span>
              </button>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* Input Bar */}
        <div className="p-3.5 bg-zinc-950 border-t border-zinc-800">
          {/* Active Product Context Badge */}
          {activeProductId && (
            <div className="mb-2.5 px-3 py-2 bg-zinc-900/90 border border-zinc-800 rounded-xl flex items-center justify-between gap-3 text-xs animate-fade-in">
              <div className="flex items-center gap-2.5 min-w-0">
                <span className="px-2 py-0.5 rounded-md bg-indigo-500/10 border border-indigo-500/30 text-[10px] font-bold text-indigo-400 uppercase tracking-wider flex items-center gap-1 shrink-0">
                  <Sparkles size={11} />
                  Active Product
                </span>
                <div className="h-6 w-6 rounded-md overflow-hidden border border-zinc-700 shrink-0 flex items-center justify-center bg-zinc-900">
                  <ProductImage
                    src={activeProduct?.imageUrl}
                    alt={activeProduct?.name || 'Product'}
                    className="h-full w-full object-cover"
                  />
                </div>
                <div className="flex items-center gap-2 truncate">
                  <span className="font-semibold text-zinc-200 truncate">
                    {activeProduct?.name || `Product: ${activeProductId.slice(0, 8)}...`}
                  </span>
                  {activeProduct?.brand && (
                    <span className="text-[10px] text-zinc-500 shrink-0 hidden sm:inline">
                      · {activeProduct.brand}
                    </span>
                  )}
                  {activeProduct?.lowestPrice && (
                    <span className="text-[10px] font-semibold text-emerald-400 shrink-0 hidden sm:inline">
                      ({formatPrice(getDisplayPrice(activeProduct.lowestPrice, userCurrency), userCurrency)})
                    </span>
                  )}
                </div>
              </div>
              <button
                type="button"
                onClick={handleClearProductContext}
                aria-label="Clear active product context"
                className="p-1 rounded-lg text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800 transition-colors cursor-pointer shrink-0"
                title="Clear active product context"
              >
                <X size={14} />
              </button>
            </div>
          )}

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
              placeholder={loading ? "Analyzing shopping context..." : "Ask a shopping question (e.g. 'Compare top noise-canceling headphones' or 'Should I buy now?')"}
              className="flex-grow bg-transparent text-xs sm:text-sm text-zinc-100 placeholder-zinc-500 focus:outline-none py-1.5"
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
    </div>
  );
};
