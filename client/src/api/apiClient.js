/**
 * DocuMindAI API Client
 * Interfaces with the Spring Boot backend REST endpoints:
 *  - POST /api/documents/upload (multipart/form-data, field: "files")
 *  - POST /api/documents/ask (application/json, body: { question })
 */

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';

export const MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024; // 20 MB per file limit
export const MAX_FILE_COUNT = 10; // Max 10 files per upload batch

/**
 * Check if the file extension is supported (PDF or DOCX)
 */
export function isSupportedFileType(filename) {
  if (!filename) return false;
  const lower = filename.toLowerCase();
  return lower.endsWith('.pdf') || lower.endsWith('.docx');
}

/**
 * Format raw byte size into human readable string (KB / MB)
 */
export function formatFileSize(bytes) {
  if (!bytes || bytes <= 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  const val = parseFloat((bytes / Math.pow(k, i)).toFixed(1));
  return `${val} ${sizes[i]}`;
}

/**
 * Upload one or more PDF/DOCX document files.
 * @param {File[]} fileArray - List of File objects
 * @returns {Promise<{ message: string, totalFiles: number, files: Array }>}
 */
export async function uploadDocuments(fileArray) {
  if (!fileArray || fileArray.length === 0) {
    throw new Error('Please select at least one document to upload.');
  }

  const formData = new FormData();
  for (const file of fileArray) {
    formData.append('files', file);
  }

  try {
    const response = await fetch(`${API_BASE}/api/documents/upload`, {
      method: 'POST',
      body: formData,
    });

    const data = await response.json().catch(() => null);

    if (!response.ok) {
      const errorMsg = data?.message || data?.error || `Upload failed with HTTP ${response.status}`;
      const err = new Error(errorMsg);
      err.status = response.status;
      err.details = data;
      throw err;
    }

    return data;
  } catch (err) {
    if (err.status) throw err;
    const netErr = new Error(err.message || 'Network error: Unable to connect to the DocuMindAI server.');
    netErr.isNetworkError = true;
    throw netErr;
  }
}

/**
 * Ask a natural-language question grounded in uploaded documents.
 * Supports English, Tanglish, Mixed, and Tamil script.
 * @param {string} question - Exact question string entered by the user
 * @returns {Promise<{ question: string, answer: string, sources: Array, insufficientEvidence: boolean }>}
 */
export async function askQuestion(question) {
  if (!question || !question.trim()) {
    throw new Error('Question must not be blank.');
  }

  try {
    const response = await fetch(`${API_BASE}/api/documents/ask`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ question: question.trim() }),
    });

    const data = await response.json().catch(() => null);

    if (!response.ok) {
      const errorMsg = data?.message || data?.error || `AI question answering failed with HTTP ${response.status}`;
      const err = new Error(errorMsg);
      err.status = response.status;
      err.details = data;
      throw err;
    }

    return data;
  } catch (err) {
    if (err.status) throw err;
    const netErr = new Error(err.message || 'Network error: Unable to reach AI document service.');
    netErr.isNetworkError = true;
    throw netErr;
  }
}

/**
 * Retrieve all persisted documents from the database.
 * @returns {Promise<Array>} List of document metadata objects
 */
export async function getDocuments() {
  try {
    const response = await fetch(`${API_BASE}/api/documents`);
    const data = await response.json().catch(() => null);

    if (!response.ok) {
      const errorMsg = data?.message || data?.error || `Failed to fetch documents (HTTP ${response.status})`;
      const err = new Error(errorMsg);
      err.status = response.status;
      err.details = data;
      throw err;
    }

    const list = Array.isArray(data) ? data : (data?.files || []);
    return list.map((doc) => ({
      id: doc.id,
      name: doc.fileName || doc.name || 'Untitled Document',
      fileName: doc.fileName || doc.name || 'Untitled Document',
      size: doc.size || doc.fileSize || 0,
      type: doc.documentType || ((doc.fileName || doc.name || '').toLowerCase().endsWith('.docx') ? 'DOCX' : 'PDF'),
      status: doc.status || 'PROCESSED',
      uploadedAt: doc.uploadTimestamp ? new Date(doc.uploadTimestamp) : new Date(),
      pageCount: doc.pageCount,
      paragraphCount: doc.paragraphCount
    }));
  } catch (err) {
    if (err.status) throw err;
    const netErr = new Error(err.message || 'Network error: Unable to load documents from server.');
    netErr.isNetworkError = true;
    throw netErr;
  }
}

/**
 * Permanently remove an uploaded document by its unique ID.
 * @param {string} documentId - Unique UUID of the document
 * @returns {Promise<{ message: string, documentId: string }>}
 */
export async function deleteDocument(documentId) {
  if (!documentId) {
    throw new Error('Document ID is required for removal.');
  }

  try {
    const response = await fetch(`${API_BASE}/api/documents/${documentId}`, {
      method: 'DELETE',
    });

    const data = await response.json().catch(() => null);

    if (!response.ok) {
      const errorMsg = data?.message || data?.error || `Failed to remove document (HTTP ${response.status})`;
      const err = new Error(errorMsg);
      err.status = response.status;
      err.details = data;
      throw err;
    }

    return data;
  } catch (err) {
    if (err.status) throw err;
    const netErr = new Error(err.message || 'Network error: Unable to connect to server to remove document.');
    netErr.isNetworkError = true;
    throw netErr;
  }
}

export default {
  getDocuments,
  uploadDocuments,
  askQuestion,
  deleteDocument,
  isSupportedFileType,
  formatFileSize,
  MAX_FILE_SIZE_BYTES,
  MAX_FILE_COUNT
};
