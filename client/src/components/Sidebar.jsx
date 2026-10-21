import {
  LogoMark,
  PlusIcon,
  PdfIcon,
  DocxIcon,
  CloseIcon,
  UploadCloudIcon,
  CheckIcon,
  TrashIcon,
  SpinnerIcon,
  ExclamationIcon,
  RefreshIcon
} from './Icons';
import { formatFileSize } from '../api/apiClient';

export function Sidebar({
  isOpen,
  onClose,
  uploadedDocuments = [],
  isLoadingDocuments = false,
  isRetryingDocuments = false,
  documentFetchError = null,
  onRetryFetchDocuments = null,
  onNewChat,
  onTriggerUpload,
  onRemoveDocument
}) {
  return (
    <>
      {/* Mobile backdrop */}
      <div
        className={`sidebar-backdrop ${isOpen ? 'is-visible' : ''}`}
        onClick={onClose}
        aria-hidden="true"
      />

      <aside
        className={`app-sidebar ${isOpen ? 'is-open' : 'is-collapsed'}`}
        id="app-sidebar-drawer"
        aria-label="Document library and navigation"
      >
        {/* Brand header */}
        <div className="sidebar-header">
          <div className="sidebar-brand-group">
            <LogoMark size={28} />
            <div className="sidebar-brand-text">
              <span className="sidebar-brand-title">DocuMindAI</span>
              <span className="sidebar-brand-tagline">Finds What Others Miss</span>
            </div>
          </div>

          <button
            type="button"
            className="sidebar-collapse-btn"
            onClick={onClose}
            title="Collapse sidebar"
            aria-label="Collapse sidebar"
          >
            <CloseIcon size={16} />
          </button>
        </div>

        {/* New Chat action */}
        <div className="sidebar-action-section">
          <button
            type="button"
            id="sidebar-new-chat-btn"
            className="sidebar-new-chat-btn"
            onClick={() => {
              onNewChat();
              if (window.innerWidth < 768) onClose();
            }}
          >
            <PlusIcon size={16} />
            <span>New Chat</span>
          </button>
        </div>

        {/* Uploaded Documents library */}
        <div className="sidebar-docs-section">
          <div className="sidebar-docs-header">
            <span className="sidebar-docs-title">
              Indexed Documents
              {uploadedDocuments.length > 0 && (
                <span className="sidebar-docs-count">{uploadedDocuments.length}</span>
              )}
            </span>

            {onTriggerUpload && (
              <button
                type="button"
                className="sidebar-add-doc-btn"
                onClick={onTriggerUpload}
                title="Attach another document"
                aria-label="Attach document"
              >
                <UploadCloudIcon size={14} />
              </button>
            )}
          </div>

          <div className="sidebar-docs-list">
            {uploadedDocuments.length > 0 && documentFetchError && (
              <div className="sidebar-stale-warning animate-fade-in" role="status">
                <ExclamationIcon size={13} className="stale-warning-icon" />
                <span className="stale-warning-text">Server offline • Showing cached docs</span>
                {onRetryFetchDocuments && (
                  <button
                    type="button"
                    className="stale-retry-btn"
                    onClick={onRetryFetchDocuments}
                    disabled={isLoadingDocuments || isRetryingDocuments}
                    title="Retry connecting to server"
                    aria-label="Retry connection"
                  >
                    <RefreshIcon size={12} className={(isLoadingDocuments || isRetryingDocuments) ? 'animate-spin' : ''} />
                  </button>
                )}
              </div>
            )}

            {isLoadingDocuments && uploadedDocuments.length === 0 ? (
              <div className="sidebar-loading-docs" role="status" aria-label="Loading documents">
                <SpinnerIcon size={18} />
                <span>{isRetryingDocuments ? 'Connecting to server...' : 'Loading documents...'}</span>
              </div>
            ) : documentFetchError && uploadedDocuments.length === 0 ? (
              <div className="sidebar-empty-docs sidebar-error-docs" role="alert">
                <div className="empty-docs-icon-box error-tint">
                  <ExclamationIcon size={20} />
                </div>
                <p className="empty-docs-title">Server Temporarily Unavailable</p>
                <p className="empty-docs-desc">{documentFetchError}</p>
                {onRetryFetchDocuments && (
                  <button
                    type="button"
                    className="empty-docs-upload-btn retry-btn"
                    onClick={onRetryFetchDocuments}
                    disabled={isLoadingDocuments || isRetryingDocuments}
                  >
                    {(isLoadingDocuments || isRetryingDocuments) ? (
                      <>
                        <SpinnerIcon size={14} />
                        <span>Reconnecting...</span>
                      </>
                    ) : (
                      'Retry'
                    )}
                  </button>
                )}
              </div>
            ) : uploadedDocuments.length === 0 ? (
              <div className="sidebar-empty-docs">
                <div className="empty-docs-icon-box">
                  <UploadCloudIcon size={20} />
                </div>
                <p className="empty-docs-title">No documents uploaded</p>
                <p className="empty-docs-desc">
                  Attach PDF or DOCX files in the composer below to begin question answering.
                </p>
                {onTriggerUpload && (
                  <button
                    type="button"
                    className="empty-docs-upload-btn"
                    onClick={onTriggerUpload}
                  >
                    Upload Document
                  </button>
                )}
              </div>
            ) : (
              uploadedDocuments.map((doc, idx) => {
                const docName = doc.name || doc.fileName || 'Untitled Document';
                const isPdf = docName.toLowerCase().endsWith('.pdf');
                return (
                  <div key={doc.id || idx} className="sidebar-doc-card">
                    <div className={`doc-card-icon-pill ${isPdf ? 'pdf-type' : 'docx-type'}`}>
                      {isPdf ? <PdfIcon size={14} /> : <DocxIcon size={14} />}
                    </div>
                    <div className="doc-card-details">
                      <span className="doc-card-name" title={docName}>
                        {docName}
                      </span>
                      <div className="doc-card-meta">
                        {doc.size > 0 && (
                          <span className="doc-card-size">{formatFileSize(doc.size)}</span>
                        )}
                        <span className="doc-card-status-pill">
                          <CheckIcon size={11} />
                          {doc.status === 'PROCESSED_OCR_REQUIRED' ? 'OCR Required' : 'Ready'}
                        </span>
                      </div>
                    </div>
                    {onRemoveDocument && (
                      <button
                        type="button"
                        className="doc-card-remove-btn"
                        onClick={(e) => {
                          e.stopPropagation();
                          onRemoveDocument(doc);
                        }}
                        title={`Remove "${docName}"`}
                        aria-label={`Remove document ${docName}`}
                      >
                        <TrashIcon size={14} />
                      </button>
                    )}
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* Footer info */}
        <div className="sidebar-footer">
          <div className="sidebar-features-chip">
            <span className="feature-dot" />
            <span>Multilingual Document Intelligence</span>
          </div>
          <p className="sidebar-footer-text">
            Answers are strictly grounded in retrieved evidence. Supports English, Tanglish, and Tamil.
          </p>
        </div>
      </aside>
    </>
  );
}

export default Sidebar;
