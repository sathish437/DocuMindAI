import React, { useState } from 'react';
import { PdfIcon, DocxIcon, FileTextIcon, ChevronDownIcon, ChevronUpIcon } from './Icons';

export function SourceCitations({ sources, activeDocumentNames }) {
  // Keep full source list collapsed by default when appropriate
  // Single source remains visible by default; multiple sources are collapsed to keep answers readable
  const [isOpen, setIsOpen] = useState(sources?.length === 1);

  if (!sources || sources.length === 0) return null;

  return (
    <div className="citations-container" aria-label="Verified document sources">
      <div className="citations-header-row">
        <button
          type="button"
          className="citations-toggle-btn"
          onClick={() => setIsOpen((prev) => !prev)}
          aria-expanded={isOpen}
          aria-label={isOpen ? "Collapse verified sources" : "Expand verified sources"}
        >
          <div className="citations-header-title">
            <span className="citations-title">Verified Sources</span>
            <span className="citations-count-pill">{sources.length}</span>
          </div>

          <span className="citations-toggle-action">
            <span>{isOpen ? 'Hide evidence' : 'View evidence'}</span>
            {isOpen ? <ChevronUpIcon size={13} /> : <ChevronDownIcon size={13} />}
          </span>
        </button>
      </div>

      {isOpen && (
        <div className="citations-grid animate-fade-in">
          {sources.map((src, index) => (
            <SourceCard
              key={index}
              source={src}
              index={index}
              activeDocumentNames={activeDocumentNames}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function SourceCard({ source, activeDocumentNames }) {
  const [expanded, setExpanded] = useState(false);

  const isDocx = source.documentType === 'DOCX' || (source.documentName && source.documentName.toLowerCase().endsWith('.docx'));
  const isPdf = source.documentType === 'PDF' || (source.documentName && source.documentName.toLowerCase().endsWith('.pdf'));

  const isStale = Boolean(
    activeDocumentNames &&
    source.documentName &&
    !activeDocumentNames.has(source.documentName.toLowerCase())
  );

  // Location label
  let locationLabel = null;
  if (source.pageNumber != null) {
    locationLabel = `Page ${source.pageNumber}`;
  } else if (source.paragraphIndex != null) {
    locationLabel = `Para ${source.paragraphIndex}`;
  }

  const excerptText = source.excerpt ? source.excerpt.trim() : '';
  const isLong = excerptText.length > 180;
  const displayText = expanded || !isLong ? excerptText : excerptText.slice(0, 180) + '...';

  return (
    <div className={`citation-card ${expanded ? 'is-expanded' : ''} ${isStale ? 'is-stale' : ''}`}>
      <div className="citation-card-header">
        <div className="citation-doc-info">
          <span className={`file-type-icon ${isPdf ? 'is-pdf' : isDocx ? 'is-docx' : ''}`}>
            {isPdf ? <PdfIcon size={14} /> : isDocx ? <DocxIcon size={14} /> : <FileTextIcon size={14} />}
          </span>
          <span className="citation-filename" title={source.documentName}>
            {source.documentName || 'Document'}
            {isStale && (
              <span className="citation-stale-tag" title="This document was removed from the active library">
                Removed
              </span>
            )}
          </span>
        </div>

        {locationLabel && (
          <span className="citation-location-badge">{locationLabel}</span>
        )}
      </div>

      {source.heading && (
        <div className="citation-heading">
          Section: <span>{source.heading}</span>
        </div>
      )}

      {excerptText && (
        <div className="citation-body">
          <blockquote className="citation-quote">
            "{displayText}"
          </blockquote>

          {isLong && (
            <button
              type="button"
              className="citation-expand-btn"
              onClick={() => setExpanded(!expanded)}
              aria-label={expanded ? "Show less excerpt" : "Show full excerpt"}
            >
              {expanded ? (
                <>Show less <ChevronUpIcon size={12} /></>
              ) : (
                <>Read full evidence <ChevronDownIcon size={12} /></>
              )}
            </button>
          )}
        </div>
      )}
    </div>
  );
}

export default SourceCitations;
