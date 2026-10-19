import React, { useEffect, useRef } from 'react';
import { TrashIcon, CloseIcon, SpinnerIcon, ExclamationIcon } from './Icons';

/**
 * Accessible confirmation dialog for removing an uploaded document.
 * Adheres to WAI-ARIA modal dialog specifications:
 * - Traps focus while active
 * - Escape key dismisses the dialog
 * - Auto-focuses the safe default (Cancel) action
 * - Prevents interaction with outside elements
 */
export function RemoveDocumentModal({
  isOpen,
  document,
  onClose,
  onConfirm,
  isProcessing = false,
  error = null,
  onRetry = null
}) {
  const cancelBtnRef = useRef(null);
  const modalRef = useRef(null);
  const previousActiveElement = useRef(null);

  // Capture previous active element and trap focus
  useEffect(() => {
    if (isOpen) {
      previousActiveElement.current = window.document.activeElement;

      // Focus safe action (Cancel) on open
      const timer = setTimeout(() => {
        if (cancelBtnRef.current) {
          cancelBtnRef.current.focus();
        }
      }, 50);

      // Prevent background scrolling
      window.document.body.style.overflow = 'hidden';

      return () => {
        clearTimeout(timer);
        window.document.body.style.overflow = '';
        if (previousActiveElement.current && typeof previousActiveElement.current.focus === 'function') {
          previousActiveElement.current.focus();
        }
      };
    }
  }, [isOpen]);

  // Handle keyboard events (Escape to dismiss, Tab trapping)
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        if (!isProcessing) {
          e.preventDefault();
          onClose();
        }
        return;
      }

      if (e.key === 'Tab') {
        const modal = modalRef.current;
        if (!modal) return;

        const focusableElements = modal.querySelectorAll(
          'button:not([disabled]), [tabindex]:not([tabindex="-1"])'
        );
        const firstElement = focusableElements[0];
        const lastElement = focusableElements[focusableElements.length - 1];

        if (e.shiftKey) {
          if (window.document.activeElement === firstElement) {
            e.preventDefault();
            lastElement?.focus();
          }
        } else {
          if (window.document.activeElement === lastElement) {
            e.preventDefault();
            firstElement?.focus();
          }
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, isProcessing, onClose]);

  if (!isOpen || !document) return null;

  const fileName = document.name || document.fileName || 'this document';

  return (
    <div
      className="modal-backdrop animate-fade-in"
      onClick={(e) => {
        if (e.target === e.currentTarget && !isProcessing) {
          onClose();
        }
      }}
      role="presentation"
    >
      <div
        className="modal-dialog"
        ref={modalRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="remove-doc-title"
        aria-describedby="remove-doc-desc"
      >
        {/* Header */}
        <div className="modal-header">
          <div className="modal-title-group">
            <div className="modal-danger-icon-box">
              <TrashIcon size={18} />
            </div>
            <h3 id="remove-doc-title" className="modal-title">
              Remove Document?
            </h3>
          </div>

          <button
            type="button"
            className="modal-close-btn"
            onClick={onClose}
            disabled={isProcessing}
            aria-label="Close dialog"
            title="Close dialog"
          >
            <CloseIcon size={14} />
          </button>
        </div>

        {/* Body */}
        <div className="modal-body">
          <p id="remove-doc-desc" className="modal-message">
            Are you sure you want to remove &ldquo;<strong>{fileName}</strong>&rdquo;? This action cannot be undone.
          </p>

          {error && (
            <div className="modal-error-alert" role="alert">
              <ExclamationIcon size={16} className="modal-error-icon" />
              <div className="modal-error-text">
                <span>{error}</span>
                {onRetry && (
                  <button
                    type="button"
                    className="modal-retry-inline-btn"
                    onClick={onRetry}
                    disabled={isProcessing}
                  >
                    Retry
                  </button>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Footer actions */}
        <div className="modal-footer">
          <button
            type="button"
            ref={cancelBtnRef}
            className="btn-modal-cancel"
            onClick={onClose}
            disabled={isProcessing}
          >
            Cancel
          </button>

          <button
            type="button"
            className="btn-modal-remove"
            onClick={() => onConfirm(document)}
            disabled={isProcessing}
            aria-busy={isProcessing}
          >
            {isProcessing ? (
              <>
                <SpinnerIcon size={14} />
                <span>Removing...</span>
              </>
            ) : (
              <span>Remove</span>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

export default RemoveDocumentModal;
