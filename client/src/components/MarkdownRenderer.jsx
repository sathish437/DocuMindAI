import React from 'react';

/**
 * Lightweight safe Markdown renderer tailored for AI document analysis responses.
 * Formats headers, bullet lists, claim blocks, bold, italics, code snippets, and citations.
 */
export function MarkdownRenderer({ content }) {
  if (!content) return null;

  // Split into lines
  const lines = content.split('\n');
  const elements = [];
  let inList = false;
  let listItems = [];
  let keyCounter = 0;

  const flushList = () => {
    if (inList && listItems.length > 0) {
      elements.push(
        <ul key={`list-${keyCounter++}`} className="md-list">
          {listItems.map((item, idx) => (
            <li key={idx} className="md-list-item">{renderInline(item)}</li>
          ))}
        </ul>
      );
      listItems = [];
      inList = false;
    }
  };

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trim();

    if (!trimmed) {
      flushList();
      continue;
    }

    // Headings (### or ## or #)
    if (trimmed.startsWith('### ')) {
      flushList();
      elements.push(
        <h3 key={`h3-${keyCounter++}`} className="md-heading-3">
          {renderInline(trimmed.substring(4))}
        </h3>
      );
      continue;
    }

    if (trimmed.startsWith('## ')) {
      flushList();
      elements.push(
        <h2 key={`h2-${keyCounter++}`} className="md-heading-2">
          {renderInline(trimmed.substring(3))}
        </h2>
      );
      continue;
    }

    if (trimmed.startsWith('# ')) {
      flushList();
      elements.push(
        <h1 key={`h1-${keyCounter++}`} className="md-heading-1">
          {renderInline(trimmed.substring(2))}
        </h1>
      );
      continue;
    }

    // Bullet points (•, -, *, or numbered like 1.)
    const isBullet = trimmed.startsWith('• ') || trimmed.startsWith('- ') || trimmed.startsWith('* ');
    const isNumbered = /^\d+\.\s/.test(trimmed);

    if (isBullet || isNumbered) {
      inList = true;
      const text = isBullet ? trimmed.substring(2) : trimmed.replace(/^\d+\.\s/, '');
      listItems.push(text);
      continue;
    }

    // If regular paragraph
    flushList();
    elements.push(
      <p key={`p-${keyCounter++}`} className="md-paragraph">
        {renderInline(trimmed)}
      </p>
    );
  }

  flushList();

  return <div className="markdown-content">{elements}</div>;
}

/**
 * Format inline text: bold (**), italic (*), citations ([...]), and inline code (`...`)
 */
function renderInline(text) {
  if (!text) return text;

  // Regex to match markdown inline formatting:
  // 1: Bold `**bold**`
  // 2: Inline code `code`
  // 3: Source citations `[filename.pdf, Page X]`
  const tokenRegex = /(\*\*[^*]+\*\*|`[^`]+`|\[[a-zA-Z0-9_\-.\s]+,\s*(?:Page|Para)\s*\d+[^\]]*\])/g;

  const parts = text.split(tokenRegex);

  return parts.map((part, index) => {
    if (!part) return null;

    // Bold
    if (part.startsWith('**') && part.endsWith('**') && part.length > 4) {
      return <strong key={index} className="md-bold">{part.slice(2, -2)}</strong>;
    }

    // Inline code
    if (part.startsWith('`') && part.endsWith('`') && part.length > 2) {
      return <code key={index} className="md-inline-code">{part.slice(1, -1)}</code>;
    }

    // Source citation link badge e.g. [Policy.pdf, Page 1]
    if (part.startsWith('[') && part.endsWith(']') && (part.includes('Page') || part.includes('Para'))) {
      return (
        <span key={index} className="md-citation-badge" title="Verified document citation">
          {part}
        </span>
      );
    }

    return part;
  });
}
