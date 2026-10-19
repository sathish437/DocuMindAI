import React from 'react';
import { SparklesIcon, FileTextIcon } from './Icons';

const SUGGESTIONS = [
  {
    title: 'Summarize my document',
    desc: 'Extract key points, structure, and executive takeaways',
    prompt: 'Summarize my document'
  },
  {
    title: 'Find important requirements',
    desc: 'Identify critical policies, limits, rules, and conditions',
    prompt: 'Find important requirements'
  },
  {
    title: 'Compare uploaded documents',
    desc: 'Examine cross-document references and aligned clauses',
    prompt: 'Compare uploaded documents'
  },
  {
    title: 'Find contradictions',
    desc: 'Detect conflicting statements, dates, limits, or rules',
    prompt: 'Find contradictions'
  }
];

export function WelcomeState({ onSelectSuggestion, hasDocuments }) {
  return (
    <div className="welcome-state-container animate-fade-in" id="welcome-state">
      <div className="welcome-icon-wrapper" aria-hidden="true">
        <div className="welcome-icon-glow" />
        <div className="welcome-icon-badge">
          <FileTextIcon size={32} className="welcome-document-icon" />
          <span className="welcome-sparkle-dot">
            <SparklesIcon size={14} />
          </span>
        </div>
      </div>

      <div className="welcome-header">
        <h1 className="welcome-title">Your documents. Your answers.</h1>
        <p className="welcome-subtitle">
          Upload your files and ask questions. Get answers grounded in your documents.
        </p>
      </div>

      <div className="welcome-multilingual-badge">
        <span className="multilingual-dot" />
        <span>Ask in English, Tanglish, or தமிழ் script</span>
      </div>

      <div className="welcome-suggestions-grid">
        {SUGGESTIONS.map((item, index) => (
          <button
            key={index}
            id={`suggestion-chip-${index}`}
            type="button"
            className="suggestion-chip-card"
            onClick={() => onSelectSuggestion(item.prompt)}
          >
            <div className="suggestion-chip-header">
              <span className="suggestion-chip-title">{item.title}</span>
              <span className="suggestion-chip-arrow" aria-hidden="true">→</span>
            </div>
            <p className="suggestion-chip-desc">{item.desc}</p>
          </button>
        ))}
      </div>

      {!hasDocuments && (
        <div className="welcome-upload-hint">
          <span>Tip: Attach your PDF or DOCX file using the paperclip button below to get started.</span>
        </div>
      )}
    </div>
  );
}

export default WelcomeState;
