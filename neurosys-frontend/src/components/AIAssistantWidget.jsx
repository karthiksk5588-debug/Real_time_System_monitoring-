import React, { useState } from 'react';
import { metricsService } from '../services/metricsService';
import { Bot, Send, X, Sparkles, MessageSquare } from 'lucide-react';

const formatMarkdown = (text) => {
  if (!text) return '';
  // Convert **bold** to <strong>
  const parts = text.split(/(\*\*.*?\*\*)/g);
  return parts.map((part, index) => {
    if (part.startsWith('**') && part.endsWith('**')) {
      return <strong key={index} className="font-semibold text-cyan-300">{part.slice(2, -2)}</strong>;
    }
    return part;
  });
};

const AIAssistantWidget = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState([
    {
      sender: 'bot',
      text: "Hello! I am NeuroSys AI Assistant Copilot powered by Google Gemini. Ask me about system telemetry, missing software, internet connectivity, or individual computer reports.",
      recommendations: [
        "Which computers don't have Java 21?",
        "Which computers have no internet?",
        "Which computers need attention?",
        "Report for LAB-01-PC01"
      ],
    },
  ]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSend = async (textToSend) => {
    const queryText = textToSend || input;
    if (!queryText.trim() || loading) return;

    const userMsg = { sender: 'user', text: queryText };
    setMessages((prev) => [...prev, userMsg]);
    if (!textToSend) setInput('');
    setLoading(true);

    try {
      const res = await metricsService.askAiAssistant(queryText);
      console.log("AI Assistant response payload:", res);
      const dataObj = res?.data || res;
      const answerText = dataObj?.answer || (typeof res === 'string' ? res : 'No response text received.');
      const recs = dataObj?.optimizationRecommendations || [];

      const botMsg = {
        sender: 'bot',
        text: answerText,
        recommendations: recs,
      };
      setMessages((prev) => [...prev, botMsg]);
    } catch (err) {
      console.error("AI Assistant error:", err);
      setMessages((prev) => [
        ...prev,
        { sender: 'bot', text: 'Sorry, I encountered an error querying live telemetry backend. Please check network connection.' },
      ]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed bottom-4 right-4 sm:bottom-6 sm:right-6 z-50">
      {!isOpen ? (
        <button
          onClick={() => setIsOpen(true)}
          className="flex items-center space-x-2 px-3.5 py-2.5 sm:px-4 sm:py-3 rounded-full bg-gradient-to-r from-cyan-500 to-blue-600 text-white font-semibold text-xs shadow-xl shadow-cyan-500/25 hover:scale-105 transition-all duration-300"
        >
          <Bot className="w-4 h-4 sm:w-5 sm:h-5" />
          <span>AI Assistant</span>
        </button>
      ) : (
        <div className="w-[calc(100vw-2rem)] sm:w-96 max-w-sm glass-panel rounded-2xl border border-slate-700 shadow-2xl overflow-hidden flex flex-col h-[480px] sm:h-[520px] transition-all duration-300">
          {/* Header */}
          <div className="p-3.5 sm:p-4 bg-gradient-to-r from-slate-900 to-slate-800 border-b border-slate-700 flex items-center justify-between">
            <div className="flex items-center space-x-2.5">
              <div className="p-1.5 sm:p-2 rounded-lg bg-cyan-500/20 text-cyan-400">
                <Bot className="w-4 h-4 sm:w-5 sm:h-5" />
              </div>
              <div>
                <h4 className="text-xs sm:text-sm font-bold text-slate-100">NeuroSys AI Assistant</h4>
                <p className="text-[9px] sm:text-[10px] text-cyan-400 font-medium">Google Gemini Live Telemetry</p>
              </div>
            </div>
            <button onClick={() => setIsOpen(false)} className="text-slate-400 hover:text-white p-1 rounded-lg">
              <X className="w-4 h-4" />
            </button>
          </div>

          {/* Messages */}
          <div className="flex-1 p-3 sm:p-4 overflow-y-auto space-y-3 text-xs">
            {messages.map((m, idx) => (
              <div key={idx} className={`flex flex-col ${m.sender === 'user' ? 'items-end' : 'items-start'}`}>
                <div
                  className={`max-w-[88%] p-3 rounded-2xl whitespace-pre-wrap ${
                    m.sender === 'user'
                      ? 'bg-gradient-to-r from-cyan-500 to-blue-600 text-white rounded-br-none'
                      : 'bg-slate-800/90 text-slate-200 border border-slate-700 rounded-bl-none'
                  }`}
                >
                  {formatMarkdown(m.text)}
                </div>

                {m.recommendations && m.recommendations.length > 0 && (
                  <div className="mt-2 space-y-1.5 w-full">
                    {m.recommendations.map((rec, rIdx) => (
                      <button
                        key={rIdx}
                        onClick={() => handleSend(rec)}
                        className="block w-full text-left p-2 rounded-xl bg-cyan-500/10 hover:bg-cyan-500/20 border border-cyan-500/20 text-[11px] text-cyan-300 transition-colors"
                      >
                        💡 {rec}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            ))}
            {loading && <div className="text-cyan-400 text-xs italic flex items-center space-x-1"><Sparkles className="w-3 h-3 animate-spin" /><span>Gemini AI is analyzing live telemetry...</span></div>}
          </div>

          {/* Input */}
          <div className="p-3 border-t border-slate-800 bg-slate-900/90 flex items-center space-x-2">
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSend()}
              placeholder="Ask: 'Report for LAB-01-PC01'..."
              className="flex-1 px-3 py-2 bg-slate-800 border border-slate-700 rounded-xl text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-cyan-500"
            />
            <button
              onClick={() => handleSend()}
              className="p-2 rounded-xl bg-cyan-500 text-white hover:bg-cyan-600 transition-colors"
            >
              <Send className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default AIAssistantWidget;
