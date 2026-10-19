import React, { useState } from 'react';
import { MarkdownRenderer } from './MarkdownRenderer';
import { SourceCitations } from './SourceCitations';
import {
  LogoMark,
  CopyIcon,
  CheckIcon,
  PdfIcon,
  DocxIcon,
  FileTextIcon,
  ExclamationIcon,
  RefreshIcon
} from './Icons';

export function MessageItem({ message, onRetry, activeDocumentNames }) {
  const [copied, setCopied] = useState(false);

  const isUser = message.sender === 'user' || message.role === 'user';
  const textContent = message.text || message.content || '';
  const isError = Boolean(message.isError);

  const handleCopy = async () => {
    if (!textContent) return;
    try {
      await navigator.clipboard.writeText(textContent);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // fallback
    }
  };

  const formattedTime = message.timestamp
    ? new Date(message.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    : null;

  if (isUser) {
    return (
      <div className="message-item is-user animate-fade-in" id={`msg-${message.id}`}>
        <div className="message-avatar-wrap">
          <div className="message-avatar-user" title="You">
            <span>U</span>
          </div>
        </div>

        <div className="message-bubble-container">
          <div className="message-bubble user-bubble">
            {message.attachments && message.attachments.length > 0 && (
              <div className="user-message-attachments">
                {message.attachments.map((att, idx) => {
                  const filename = typeof att === 'string' ? att : att?.name || 'document';
                  const isPdf = filename.toLowerCase().endsWith('.pdf');
                  const isDocx = filename.toLowerCase().endsWith('.docx');
                  return (
                    <span key={idx} className="user-attachment-pill" title={filename}>
                      {isPdf ? (
                        <PdfIcon size={12} />
                      ) : isDocx ? (
                        <DocxIcon size={12} />
                      ) : (
                        <FileTextIcon size={12} />
                      )}
                      <span>{filename}</span>
                    </span>
                  );
                })}
              </div>
            )}

            <div className="user-text-content">{textContent}</div>
          </div>

          {formattedTime && (
            <div className="message-meta-actions">
              <span className="message-timestamp">{formattedTime}</span>
            </div>
          )}
        </div>
      </div>
    );
  }

  // Assistant Message
  return (
    <div
      className={`message-item is-assistant animate-fade-in ${isError ? 'has-error' : ''}`}
      id={`msg-${message.id}`}
    >
      <div className="message-avatar-wrap">
        <div className="message-avatar-ai" title="DocuMindAI">
          <LogoMark size={26} />
        </div>
      </div>

      <div className="message-bubble-container">
        <div className={`message-bubble assistant-bubble ${isError ? 'error-bubble' : ''}`}>
          {message.insufficientEvidence && (
            <div className="insufficient-evidence-banner">
              <ExclamationIcon size={14} className="evidence-alert-icon" />
              <span>Limited document evidence found for this query</span>
            </div>
          )}

          <div className="ai-markdown-wrap">
            <MarkdownRenderer content={textContent} />
          </div>

          <div className="message-meta-actions">
            {!isError && (
              <button
                type="button"
                className="message-action-btn copy-btn"
                onClick={handleCopy}
                title="Copy response to clipboard"
                aria-label="Copy response"
              >
                {copied ? <CheckIcon size={12} /> : <CopyIcon size={12} />}
                <span>{copied ? 'Copied' : 'Copy'}</span>
              </button>
            )}

            {isError && onRetry && (
              <button
                type="button"
                className="message-action-btn retry-btn"
                onClick={() => onRetry(message)}
                title="Retry asking this question"
                aria-label="Retry question"
              >
                <RefreshIcon size={12} />
                <span>Retry</span>
              </button>
            )}

            {formattedTime && (
              <span className="message-timestamp">{formattedTime}</span>
            )}
          </div>
        </div>

        {/* Source Citations */}
        {message.sources && message.sources.length > 0 && (
          <SourceCitations sources={message.sources} activeDocumentNames={activeDocumentNames} />
        )}
      </div>
    </div>
  );
}

export default MessageItem;
