import React, { useRef, useEffect, useState } from 'react';
import {
  PaperclipIcon,
  SendIcon,
  PdfIcon,
  DocxIcon,
  CloseIcon,
  SpinnerIcon,
  CheckIcon,
  ExclamationIcon,
  RefreshIcon
} from './Icons';
import { MAX_FILE_SIZE_BYTES, MAX_FILE_COUNT, formatFileSize, isSupportedFileType } from '../api/apiClient';

export function ChatComposer({
  question,
  setQuestion,
  onSend,
  isGenerating,
  isUploading,
  hasUploadedDocs,
  pendingAttachments,
  setPendingAttachments,
  onRetryUpload
}) {
  const textareaRef = useRef(null);
  const fileInputRef = useRef(null);
  const [validationError, setValidationError] = useState(null);

  // Auto-resize textarea to fit content smoothly up to 180px
  useEffect(() => {
    const textarea = textareaRef.current;
    if (!textarea) return;
    textarea.style.height = 'auto';
    const newHeight = Math.min(textarea.scrollHeight, 180);
    textarea.style.height = `${Math.max(newHeight, 44)}px`;
  }, [question]);

  const handleOpenPicker = () => {
    if (isUploading || isGenerating) return;
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
      fileInputRef.current.click();
    }
  };

  const handleFileSelect = (e) => {
    const selectedFiles = Array.from(e.target.files || []);
    if (selectedFiles.length === 0) return;

    setValidationError(null);

    // Check count limit
    const totalCount = pendingAttachments.length + selectedFiles.length;
    if (totalCount > MAX_FILE_COUNT) {
      setValidationError(`Maximum ${MAX_FILE_COUNT} files allowed. You have ${pendingAttachments.length} and selected ${selectedFiles.length}.`);
      return;
    }

    const newAttachments = [];
    const errors = [];

    selectedFiles.forEach((file) => {
      // Check duplicate in pending list
      const alreadyPending = pendingAttachments.some(
        (att) => att.name.toLowerCase() === file.name.toLowerCase() && att.size === file.size
      );
      if (alreadyPending) {
        errors.push(`"${file.name}" is already attached.`);
        return;
      }

      // Check format
      if (!isSupportedFileType(file.name)) {
        errors.push(`"${file.name}" is not supported. Please upload PDF or DOCX files.`);
        return;
      }

      // Check file size
      if (file.size > MAX_FILE_SIZE_BYTES) {
        errors.push(`"${file.name}" exceeds the 20MB limit (${formatFileSize(file.size)}).`);
        return;
      }

      newAttachments.push({
        id: `att-${Date.now()}-${Math.random().toString(36).substr(2, 6)}`,
        file,
        name: file.name,
        size: file.size,
        type: file.name.toLowerCase().endsWith('.pdf') ? 'pdf' : 'docx',
        status: 'pending', // 'pending' | 'uploading' | 'completed' | 'failed'
        errorMsg: null
      });
    });

    if (errors.length > 0) {
      setValidationError(errors.join(' '));
    }

    if (newAttachments.length > 0) {
      setPendingAttachments((prev) => [...prev, ...newAttachments]);
    }
  };

  const handleRemoveAttachment = (id) => {
    setPendingAttachments((prev) => prev.filter((item) => item.id !== id));
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleTriggerSubmit();
    }
  };

  const handleTriggerSubmit = () => {
    const trimmed = question.trim();
    const hasPendingFiles = pendingAttachments.length > 0;

    // Must have either a question or pending files to upload
    if (!trimmed && !hasPendingFiles) return;
    if (isGenerating || isUploading) return;

    onSend(trimmed, pendingAttachments);
  };

  const isBusy = isGenerating || isUploading;
  const canSubmit = !isBusy && (question.trim().length > 0 || pendingAttachments.length > 0);

  return (
    <div className="composer-wrapper" id="chat-composer-section">
      {/* Validation warning */}
      {validationError && (
        <div className="composer-validation-banner animate-fade-in" role="alert">
          <ExclamationIcon size={16} className="validation-icon" />
          <span className="validation-text">{validationError}</span>
          <button
            type="button"
            className="validation-close-btn"
            onClick={() => setValidationError(null)}
            aria-label="Dismiss error"
          >
            <CloseIcon size={12} />
          </button>
        </div>
      )}

      {/* Attachment chips preview */}
      {pendingAttachments.length > 0 && (
        <div className="composer-attachments-row" aria-label="Attached files">
          {pendingAttachments.map((att) => {
            const isPdf = att.type === 'pdf';
            return (
              <div
                key={att.id}
                className={`attachment-chip status-${att.status}`}
                title={att.name}
              >
                <div className={`attachment-icon-pill ${isPdf ? 'pdf-type' : 'docx-type'}`}>
                  {isPdf ? <PdfIcon size={14} /> : <DocxIcon size={14} />}
                </div>

                <div className="attachment-chip-meta">
                  <span className="attachment-chip-name">{att.name}</span>
                  <span className="attachment-chip-size">
                    {formatFileSize(att.size)}
                    {att.status === 'uploading' && ' • Uploading...'}
                    {att.status === 'completed' && ' • Indexed'}
                    {att.status === 'failed' && ' • Failed'}
                  </span>
                </div>

                {att.status === 'uploading' && (
                  <div className="attachment-chip-status" title="Uploading & indexing">
                    <SpinnerIcon size={14} className="attachment-spinner" />
                  </div>
                )}

                {att.status === 'completed' && (
                  <div className="attachment-chip-status success" title="Successfully indexed">
                    <CheckIcon size={14} />
                  </div>
                )}

                {att.status === 'failed' && (
                  <div className="attachment-chip-actions">
                    {onRetryUpload && (
                      <button
                        type="button"
                        className="attachment-retry-btn"
                        onClick={() => onRetryUpload(att.id)}
                        title={`Retry upload: ${att.errorMsg || 'Failed'}`}
                        aria-label={`Retry upload for ${att.name}`}
                      >
                        <RefreshIcon size={13} />
                      </button>
                    )}
                  </div>
                )}

                {att.status !== 'uploading' && (
                  <button
                    type="button"
                    className="attachment-remove-btn"
                    onClick={() => handleRemoveAttachment(att.id)}
                    title="Remove attachment"
                    aria-label={`Remove ${att.name}`}
                  >
                    <CloseIcon size={12} />
                  </button>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Main composer bar */}
      <div className={`composer-input-container ${isBusy ? 'is-busy' : ''}`}>
        {/* Hidden native file input */}
        <input
          ref={fileInputRef}
          type="file"
          id="documind-file-input"
          aria-label="Upload PDF or DOCX documents"
          multiple
          accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
          style={{ display: 'none' }}
          onChange={handleFileSelect}
        />

        {/* Attachment button */}
        <button
          type="button"
          id="composer-attach-btn"
          className="composer-action-btn attach-btn"
          onClick={handleOpenPicker}
          disabled={isBusy}
          title="Attach PDF or DOCX documents"
          aria-label="Attach documents"
        >
          <PaperclipIcon size={18} />
          {pendingAttachments.length > 0 && (
            <span className="attach-count-badge" aria-label={`${pendingAttachments.length} files attached`}>
              {pendingAttachments.length}
            </span>
          )}
        </button>

        {/* Multiline textarea */}
        <textarea
          ref={textareaRef}
          id="composer-textarea"
          className="composer-textarea"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={
            hasUploadedDocs
              ? 'Ask anything about your documents... (English, Tanglish, தமிழ்)'
              : pendingAttachments.length > 0
              ? 'Add an optional question to ask upon upload...'
              : 'Ask anything about your documents, or attach files to begin...'
          }
          rows={1}
          disabled={isBusy}
          aria-label="Ask a question about your documents"
        />

        {/* Send button */}
        <button
          type="button"
          id="composer-send-btn"
          className={`composer-action-btn send-btn ${canSubmit ? 'can-send' : ''}`}
          onClick={handleTriggerSubmit}
          disabled={!canSubmit}
          title={
            isBusy
              ? 'Processing...'
              : pendingAttachments.length > 0 && !question.trim()
              ? 'Upload attached documents'
              : 'Send message (Enter)'
          }
          aria-label={isBusy ? 'Processing' : 'Send message'}
        >
          {isBusy ? (
            <SpinnerIcon size={18} className="send-spinner" />
          ) : (
            <SendIcon size={16} />
          )}
        </button>
      </div>

      <div className="composer-footer-hints">
        <span className="composer-hint-item">
          <kbd>Enter</kbd> to send • <kbd>Shift</kbd> + <kbd>Enter</kbd> for new line
        </span>
        <span className="composer-hint-item hint-formats">
          Supports PDF & DOCX up to 20MB
        </span>
      </div>
    </div>
  );
}

export default ChatComposer;
