import React, { useEffect, useRef } from 'react';
import MessageItem from './MessageItem';
import WelcomeState from './WelcomeState';
import { LogoMark } from './Icons';

export function MessageList({
  messages,
  isGenerating,
  onRetry,
  onEdit,
  onSelectSuggestion,
  hasDocuments,
  activeDocumentNames
}) {
  const messagesEndRef = useRef(null);
  const containerRef = useRef(null);
  const userScrolledUpRef = useRef(false);

  // Track if user scrolled up manually so we don't disrupt their reading
  const handleScroll = () => {
    const el = containerRef.current;
    if (!el) return;
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    userScrolledUpRef.current = distanceFromBottom > 120;
  };

  useEffect(() => {
    if (!userScrolledUpRef.current) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, isGenerating]);

  if (messages.length === 0 && !isGenerating) {
    return (
      <div className="message-list-empty-wrapper" ref={containerRef}>
        <WelcomeState
          onSelectSuggestion={onSelectSuggestion}
          hasDocuments={hasDocuments}
        />
      </div>
    );
  }

  return (
    <div
      className="message-list-container"
      ref={containerRef}
      onScroll={handleScroll}
      id="message-list-scrollable"
    >
      <div className="message-list-inner">
        {messages.map((msg, index) => (
          <MessageItem
            key={msg.id || index}
            message={msg}
            onRetry={onRetry}
            onEdit={onEdit}
            isGenerating={isGenerating}
            activeDocumentNames={activeDocumentNames}
          />
        ))}

        {isGenerating && (
          <div className="ai-thinking-indicator animate-fade-in" aria-live="polite">
            <div className="ai-thinking-avatar">
              <LogoMark size={24} />
            </div>
            <div className="ai-thinking-bubble">
              <div className="ai-thinking-dots">
                <span className="dot dot-1" />
                <span className="dot dot-2" />
                <span className="dot dot-3" />
              </div>
              <span className="ai-thinking-label">
                Reading documents and retrieving evidence...
              </span>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} className="messages-end-anchor" aria-hidden="true" />
      </div>
    </div>
  );
}

export default MessageList;
