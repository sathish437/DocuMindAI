import React from 'react';
import { ExclamationIcon, CloseIcon, RefreshIcon } from './Icons';

export function ErrorBanner({ message, onRetry, onClose, onDismiss }) {
  if (!message) return null;

  const handleClose = onClose || onDismiss;

  return (
    <div className="error-banner animate-fade-in" role="alert">
      <div className="error-banner-content">
        <ExclamationIcon size={16} className="error-icon" />
        <span className="error-banner-text">{message}</span>
      </div>
      <div className="error-banner-actions">
        {onRetry && (
          <button
            type="button"
            className="error-retry-btn"
            onClick={onRetry}
            aria-label="Retry operation"
          >
            <RefreshIcon size={12} />
            <span>Retry</span>
          </button>
        )}
        {handleClose && (
          <button
            type="button"
            className="error-close-btn"
            onClick={handleClose}
            aria-label="Dismiss error notification"
          >
            <CloseIcon size={12} />
          </button>
        )}
      </div>
    </div>
  );
}

export default ErrorBanner;
