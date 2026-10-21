import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import Header from './components/Header';
import Sidebar from './components/Sidebar';
import MessageList from './components/MessageList';
import ChatComposer from './components/ChatComposer';
import ErrorBanner from './components/ErrorBanner';
import RemoveDocumentModal from './components/RemoveDocumentModal';
import { CheckIcon, CloseIcon, ExclamationIcon } from './components/Icons';
import { getDocuments, uploadDocuments, askQuestion, deleteDocument } from './api/apiClient';
import './App.css';

const MAX_DOCUMENT_RETRIES = 3;

export function App() {
  // Chat messages
  const [messages, setMessages] = useState([]);
  
  // Track documents indexed in the database
  const [uploadedDocuments, setUploadedDocuments] = useState([]);
  const [isLoadingDocuments, setIsLoadingDocuments] = useState(true);
  const [isRetryingDocuments, setIsRetryingDocuments] = useState(false);
  const [documentFetchError, setDocumentFetchError] = useState(null);
  
  // Message currently being edited
  const [editingMessage, setEditingMessage] = useState(null);
  const savedDraftRef = useRef('');

  // Retry, timeout, and cancellation refs
  const abortControllerRef = useRef(null);
  const retryTimeoutRef = useRef(null);
  const retryCountRef = useRef(0);
  const isMountedRef = useRef(true);
  const loadDocumentsRef = useRef(null);

  // Pending attachments currently selected in the composer
  const [pendingAttachments, setPendingAttachments] = useState([]);
  
  // Input question text
  const [question, setQuestion] = useState('');
  
  // Loading flags
  const [isGenerating, setIsGenerating] = useState(false);
  const [isUploading, setIsUploading] = useState(false);

  // Document removal state & modal
  const [documentToDelete, setDocumentToDelete] = useState(null);
  const [isRemovingDocument, setIsRemovingDocument] = useState(false);
  const [removeDocError, setRemoveDocError] = useState(null);

  // User feedback toast (success or error notification)
  const [toast, setToast] = useState(null);
  
  // Layout state
  const [sidebarOpen, setSidebarOpen] = useState(() => {
    if (typeof window !== 'undefined') {
      return window.innerWidth >= 1024;
    }
    return true;
  });
  
  // Global / top-level error banner
  const [globalError, setGlobalError] = useState(null);

  // Set of active document filenames for graceful handling of stale citations
  const activeDocumentNames = useMemo(
    () => new Set(uploadedDocuments.map((d) => d.name.toLowerCase())),
    [uploadedDocuments]
  );

  // Display auto-dismissing toast feedback
  const showToast = useCallback((type, message) => {
    setToast({ type, message });
    const timer = setTimeout(() => {
      setToast((current) => (current?.message === message ? null : current));
    }, 4500);
    return () => clearTimeout(timer);
  }, []);

  // Fetch persisted documents from database with bounded exponential retry and abort cancellation
  const loadDocuments = useCallback(async (isManualRetry = false) => {
    // Clear any active retry timer to prevent race conditions
    if (retryTimeoutRef.current) {
      clearTimeout(retryTimeoutRef.current);
      retryTimeoutRef.current = null;
    }

    // Cancel in-flight request if manual retry or resetting
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }

    const controller = new AbortController();
    abortControllerRef.current = controller;

    if (isManualRetry) {
      retryCountRef.current = 0;
      setIsLoadingDocuments(true);
      setIsRetryingDocuments(false);
      setDocumentFetchError(null);
    } else if (retryCountRef.current === 0) {
      setIsLoadingDocuments(true);
      setDocumentFetchError(null);
    } else {
      setIsRetryingDocuments(true);
    }

    try {
      const docs = await getDocuments({ signal: controller.signal });
      if (!isMountedRef.current) return;

      setUploadedDocuments((prev) => {
        // Merge by document ID to avoid duplicates
        const map = new Map();
        docs.forEach((d) => {
          if (d.id) map.set(d.id, d);
        });
        prev.forEach((d) => {
          if (d.id && !map.has(d.id)) {
            map.set(d.id, d);
          }
        });
        return Array.from(map.values());
      });

      setDocumentFetchError(null);
      retryCountRef.current = 0;
      setIsRetryingDocuments(false);
    } catch (err) {
      if (!isMountedRef.current || err.name === 'AbortError') return;

      const friendlyMsg = err.message || 'The server is temporarily unavailable. Your documents will appear when the connection is restored.';
      setDocumentFetchError(friendlyMsg);

      // Bounded automatic retry with exponential backoff
      if (retryCountRef.current < MAX_DOCUMENT_RETRIES) {
        retryCountRef.current += 1;
        const delay = 1500 * Math.pow(2, retryCountRef.current - 1); // 1.5s, 3s, 6s
        setIsRetryingDocuments(true);
        retryTimeoutRef.current = setTimeout(() => {
          if (isMountedRef.current && loadDocumentsRef.current) {
            loadDocumentsRef.current(false);
          }
        }, delay);
      } else {
        setIsRetryingDocuments(false);
      }
    } finally {
      if (isMountedRef.current) {
        setIsLoadingDocuments(false);
      }
    }
  }, []);

  useEffect(() => {
    loadDocumentsRef.current = loadDocuments;
  }, [loadDocuments]);

  useEffect(() => {
    isMountedRef.current = true;
    loadDocuments(false);
    return () => {
      isMountedRef.current = false;
      if (retryTimeoutRef.current) {
        clearTimeout(retryTimeoutRef.current);
      }
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, [loadDocuments]);

  // Responsive resize handler for sidebar
  useEffect(() => {
    let wasMobile = window.innerWidth < 1024;
    const handleResize = () => {
      const isMobile = window.innerWidth < 1024;
      if (isMobile && !wasMobile) {
        setSidebarOpen(false);
      } else if (!isMobile && wasMobile) {
        setSidebarOpen(true);
      }
      wasMobile = isMobile;
    };
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  // Trigger file picker in composer from anywhere (e.g. sidebar)
  const handleTriggerUpload = useCallback(() => {
    const fileInput = document.getElementById('documind-file-input');
    if (fileInput) {
      fileInput.click();
    }
  }, []);

  // Document removal modal handlers
  const handleOpenRemoveModal = useCallback((doc) => {
    setRemoveDocError(null);
    setDocumentToDelete(doc);
  }, []);

  const handleCloseRemoveModal = useCallback(() => {
    if (isRemovingDocument) return;
    setDocumentToDelete(null);
    setRemoveDocError(null);
  }, [isRemovingDocument]);

  const handleConfirmRemoveDocument = useCallback(async (doc) => {
    if (!doc || !doc.id || isRemovingDocument) return;

    setIsRemovingDocument(true);
    setRemoveDocError(null);

    try {
      await deleteDocument(doc.id);

      // On success: remove from navbar/sidebar document list immediately
      setUploadedDocuments((prev) => prev.filter((d) => d.id !== doc.id));

      // Close confirmation dialog
      setDocumentToDelete(null);
      setRemoveDocError(null);

      // Show clear success message
      showToast('success', `Document "${doc.name}" was removed successfully.`);
    } catch (err) {
      const errorMsg = err.message || `Failed to remove "${doc.name}". Please try again.`;
      setRemoveDocError(errorMsg);
      // Keep document in the list and allow retrying
    } finally {
      setIsRemovingDocument(false);
    }
  }, [isRemovingDocument, showToast]);

  // Upload pending attachments to the backend
  const executeUpload = async (attachmentsToUpload) => {
    if (!attachmentsToUpload || attachmentsToUpload.length === 0) {
      return { success: true, count: 0, newlyUploaded: [] };
    }

    setIsUploading(true);
    setGlobalError(null);

    // Mark items as uploading
    setPendingAttachments((prev) =>
      prev.map((item) =>
        attachmentsToUpload.some((a) => a.id === item.id)
          ? { ...item, status: 'uploading', errorMsg: null }
          : item
      )
    );

    try {
      const rawFiles = attachmentsToUpload.map((item) => item.file);
      const result = await uploadDocuments(rawFiles);

      // Match each uploaded attachment with backend response to capture real persisted database UUID
      const newlyCreatedDocs = attachmentsToUpload.map((item) => {
        const backendMatch = (result.files || []).find(
          (f) => f.fileName && f.fileName.toLowerCase() === item.name.toLowerCase()
        ) || (result.files || [])[0];

        return {
          id: backendMatch?.id || item.id,
          name: item.name,
          size: item.size,
          type: item.type,
          status: 'PROCESSED',
          uploadedAt: backendMatch?.uploadTimestamp ? new Date(backendMatch.uploadTimestamp) : new Date()
        };
      });

      const uploadedNames = newlyCreatedDocs.map((d) => d.name);

      setUploadedDocuments((prev) => {
        // Prevent duplicate IDs or names in list
        const existingIds = new Set(prev.map((d) => d.id));
        const filteredNew = newlyCreatedDocs.filter((d) => !existingIds.has(d.id));
        return [...prev, ...filteredNew];
      });

      // Remove uploaded items from pending attachments
      setPendingAttachments((prev) =>
        prev.filter((item) => !attachmentsToUpload.some((a) => a.id === item.id))
      );

      return {
        success: true,
        count: uploadedNames.length,
        newlyUploaded: uploadedNames
      };
    } catch (err) {
      const errorMsg = err.message || 'Failed to upload documents. Please check file format and size.';
      
      // Mark items as failed
      setPendingAttachments((prev) =>
        prev.map((item) =>
          attachmentsToUpload.some((a) => a.id === item.id)
            ? { ...item, status: 'failed', errorMsg }
            : item
        )
      );

      setGlobalError(errorMsg);
      return { success: false, error: errorMsg, count: 0, newlyUploaded: [] };
    } finally {
      setIsUploading(false);
    }
  };

  // Retry an individual failed attachment
  const handleRetryUpload = async (attachmentId) => {
    const target = pendingAttachments.find((item) => item.id === attachmentId);
    if (!target) return;
    await executeUpload([target]);
  };

  // Start editing a user message
  const handleStartEdit = useCallback((message) => {
    if (isGenerating || !message) return;
    savedDraftRef.current = question;
    const msgIndex = messages.findIndex((m) => m.id === message.id);
    setEditingMessage({
      ...message,
      originalIndex: msgIndex
    });
    setQuestion(message.text || '');
    const textarea = document.getElementById('composer-textarea');
    if (textarea) {
      textarea.focus();
    }
  }, [isGenerating, question, messages]);

  // Cancel editing and restore previous draft
  const handleCancelEdit = useCallback(() => {
    setEditingMessage(null);
    setQuestion(savedDraftRef.current || '');
    savedDraftRef.current = '';
  }, []);

  // Resend edited user message with conversation branch consistency
  const handleResendEditedMessage = async (targetMessage, newQuestionText) => {
    const trimmed = newQuestionText.trim();
    if (!trimmed || isGenerating) return;

    // Locate the message to edit in the current message thread
    const editIndex = messages.findIndex((m) => m.id === targetMessage.id);
    if (editIndex === -1) {
      setEditingMessage(null);
      return;
    }

    const originalMsg = messages[editIndex];
    const editedUserMessage = {
      ...originalMsg,
      text: trimmed,
      isEdited: true,
      editedAt: new Date()
    };

    // Correctness: replace the edited user message and remove subsequent obsolete assistant answer & turns
    const truncatedHistory = [...messages.slice(0, editIndex), editedUserMessage];
    setMessages(truncatedHistory);
    setEditingMessage(null);
    savedDraftRef.current = '';
    setIsGenerating(true);
    setGlobalError(null);

    try {
      const response = await askQuestion(trimmed);
      const assistantMessage = {
        id: `assistant-${Date.now()}`,
        sender: 'assistant',
        text: response.answer,
        sources: response.sources || [],
        insufficientEvidence: Boolean(response.insufficientEvidence),
        timestamp: new Date()
      };
      setMessages((prev) => [...prev, assistantMessage]);
      setQuestion('');
    } catch (err) {
      const errMsg = err.message || 'Unable to retrieve answer from document intelligence engine.';
      const errorMessage = {
        id: `error-${Date.now()}`,
        sender: 'assistant',
        text: `**Service Error:** ${errMsg}`,
        isError: true,
        originalQuestion: trimmed,
        sources: [],
        timestamp: new Date()
      };
      setMessages((prev) => [...prev, errorMessage]);
      // Retain edited text in composer so user does not lose their modifications on API failure
      setQuestion(trimmed);
    } finally {
      setIsGenerating(false);
    }
  };

  // Main Send handler (question + any pending attachments)
  const handleSend = async (questionText, attachments) => {
    // If currently editing an existing message, resend through branch-consistent handler
    if (editingMessage) {
      await handleResendEditedMessage(editingMessage, questionText);
      return;
    }

    const hasFiles = attachments && attachments.length > 0;
    const hasQuestion = Boolean(questionText && questionText.trim());

    if (!hasFiles && !hasQuestion) return;

    let attachedFileNames = [];

    // Step 1: Upload files if any are pending or failed
    if (hasFiles) {
      const unuploadedFiles = attachments.filter(
        (att) => att.status === 'pending' || att.status === 'failed'
      );

      if (unuploadedFiles.length > 0) {
        const uploadResult = await executeUpload(unuploadedFiles);

        if (!uploadResult.success) {
          // Upload failed - stop here so user can retry or fix the file
          return;
        }

        attachedFileNames = uploadResult.newlyUploaded;
      }
    }

    // If user only uploaded files with no question, add a system message confirming indexing
    if (!hasQuestion) {
      if (attachedFileNames.length > 0) {
        setMessages((prev) => [
          ...prev,
          {
            id: `assistant-upload-${Date.now()}`,
            sender: 'assistant',
            text: `Successfully uploaded and indexed **${attachedFileNames.length} document${
              attachedFileNames.length === 1 ? '' : 's'
            }**: ${attachedFileNames.map((n) => `\`${n}\``).join(', ')}.\n\nYou can now ask questions about requirements, compare clauses, find contradictions, or request a summary in English, Tanglish, or Tamil.`,
            sources: [],
            insufficientEvidence: false,
            timestamp: new Date()
          }
        ]);
      }
      return;
    }

    // Step 2: User submitted a question
    const userMessageId = `user-${Date.now()}`;
    const userMessage = {
      id: userMessageId,
      sender: 'user',
      text: questionText.trim(),
      attachments: attachedFileNames.length > 0 ? attachedFileNames : undefined,
      timestamp: new Date()
    };

    setMessages((prev) => [...prev, userMessage]);
    setQuestion('');
    setIsGenerating(true);
    setGlobalError(null);

    try {
      const response = await askQuestion(questionText.trim());

      const assistantMessage = {
        id: `assistant-${Date.now()}`,
        sender: 'assistant',
        text: response.answer,
        sources: response.sources || [],
        insufficientEvidence: Boolean(response.insufficientEvidence),
        timestamp: new Date()
      };

      setMessages((prev) => [...prev, assistantMessage]);
    } catch (err) {
      const errMsg = err.message || 'Unable to retrieve answer from document intelligence engine.';
      
      const errorMessage = {
        id: `error-${Date.now()}`,
        sender: 'assistant',
        text: `**Service Error:** ${errMsg}`,
        isError: true,
        originalQuestion: questionText.trim(),
        sources: [],
        timestamp: new Date()
      };

      setMessages((prev) => [...prev, errorMessage]);
    } finally {
      setIsGenerating(false);
    }
  };

  // Retry an AI question that previously errored
  const handleRetryMessage = async (message) => {
    if (!message || !message.originalQuestion) return;
    
    // Remove the error message from the list
    setMessages((prev) => prev.filter((m) => m.id !== message.id));
    
    // Re-ask the original question
    setIsGenerating(true);
    try {
      const response = await askQuestion(message.originalQuestion);
      const assistantMessage = {
        id: `assistant-${Date.now()}`,
        sender: 'assistant',
        text: response.answer,
        sources: response.sources || [],
        insufficientEvidence: Boolean(response.insufficientEvidence),
        timestamp: new Date()
      };
      setMessages((prev) => [...prev, assistantMessage]);
    } catch (err) {
      const errMsg = err.message || 'Retry failed. Please verify server connection.';
      setMessages((prev) => [
        ...prev,
        {
          id: `error-${Date.now()}`,
          sender: 'assistant',
          text: `**Service Error:** ${errMsg}`,
          isError: true,
          originalQuestion: message.originalQuestion,
          sources: [],
          timestamp: new Date()
        }
      ]);
    } finally {
      setIsGenerating(false);
    }
  };

  // Handle clicking one of the 4 suggestion chips on the welcome screen
  const handleSelectSuggestion = (suggestionPrompt) => {
    // If documents are already loaded, ask immediately!
    if (uploadedDocuments.length > 0) {
      handleSend(suggestionPrompt, pendingAttachments);
    } else {
      // Put prompt in composer and alert user to attach documents
      setQuestion(suggestionPrompt);
      const textarea = document.getElementById('composer-textarea');
      if (textarea) {
        textarea.focus();
      }
    }
  };

  // Reset conversation to a clean slate
  const handleNewChat = () => {
    setMessages([]);
    setPendingAttachments([]);
    setQuestion('');
    setEditingMessage(null);
    savedDraftRef.current = '';
    setGlobalError(null);
  };

  return (
    <div className="documind-app" id="documind-app-root">
      {/* Collapsible Left Sidebar */}
      <Sidebar
        isOpen={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
        uploadedDocuments={uploadedDocuments}
        isLoadingDocuments={isLoadingDocuments}
        isRetryingDocuments={isRetryingDocuments}
        documentFetchError={documentFetchError}
        onRetryFetchDocuments={() => loadDocuments(true)}
        onNewChat={handleNewChat}
        onTriggerUpload={handleTriggerUpload}
        onRemoveDocument={handleOpenRemoveModal}
      />

      {/* Main Chat Workspace */}
      <div className="main-workspace">
        {/* Top Header */}
        <Header
          sidebarOpen={sidebarOpen}
          onToggleSidebar={() => setSidebarOpen((prev) => !prev)}
          onNewChat={handleNewChat}
          documentCount={uploadedDocuments.length}
          activeTitle="Document Intelligence Workspace"
        />

        {/* Global Error Banner */}
        {globalError && (
          <div className="global-error-container">
            <ErrorBanner
              message={globalError}
              onClose={() => setGlobalError(null)}
            />
          </div>
        )}

        {/* Toast Notification for Document Actions */}
        {toast && (
          <div className={`toast-notification toast-${toast.type} animate-fade-in`} role="status" aria-live="polite">
            <div className="toast-content">
              <span className={`toast-icon-badge ${toast.type}`}>
                {toast.type === 'success' ? <CheckIcon size={14} /> : <ExclamationIcon size={14} />}
              </span>
              <span className="toast-message">{toast.message}</span>
            </div>
            <button
              type="button"
              className="toast-close-btn"
              onClick={() => setToast(null)}
              aria-label="Dismiss notification"
            >
              <CloseIcon size={12} />
            </button>
          </div>
        )}

        {/* Conversation Thread / Welcome State */}
        <main className="conversation-area" id="conversation-main-content">
          <MessageList
            messages={messages}
            isGenerating={isGenerating}
            onRetry={handleRetryMessage}
            onEdit={handleStartEdit}
            onSelectSuggestion={handleSelectSuggestion}
            hasDocuments={uploadedDocuments.length > 0}
            activeDocumentNames={activeDocumentNames}
          />
        </main>

        {/* Persistent Bottom Composer */}
        <footer className="composer-footer-container">
          <ChatComposer
            question={question}
            setQuestion={setQuestion}
            onSend={handleSend}
            isGenerating={isGenerating}
            isUploading={isUploading}
            hasUploadedDocs={uploadedDocuments.length > 0}
            pendingAttachments={pendingAttachments}
            setPendingAttachments={setPendingAttachments}
            onRetryUpload={handleRetryUpload}
            editingMessage={editingMessage}
            onCancelEdit={handleCancelEdit}
          />
        </footer>
      </div>

      {/* Accessible Confirmation Modal for Removing Document */}
      <RemoveDocumentModal
        isOpen={Boolean(documentToDelete)}
        document={documentToDelete}
        onClose={handleCloseRemoveModal}
        onConfirm={handleConfirmRemoveDocument}
        isProcessing={isRemovingDocument}
        error={removeDocError}
        onRetry={() => handleConfirmRemoveDocument(documentToDelete)}
      />
    </div>
  );
}

export default App;
