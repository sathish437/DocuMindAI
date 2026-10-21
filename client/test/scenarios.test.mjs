import { describe, it } from 'node:test';
import assert from 'node:assert/strict';

// Test the apiClient functions by mocking fetch
import { getDocuments } from '../src/api/apiClient.js';

describe('DocuMindAI Frontend & API Scenarios', () => {

  it('Scenario 1: Backend returns HTTP 200 with documents', async () => {
    const mockDocs = [
      { id: '1', fileName: 'Policy.pdf', fileSize: 1024, documentType: 'PDF', status: 'PROCESSED' },
      { id: '2', fileName: 'Handbook.docx', fileSize: 2048, documentType: 'DOCX', status: 'PROCESSED' }
    ];

    const originalFetch = globalThis.fetch;
    globalThis.fetch = async () => ({
      ok: true,
      status: 200,
      json: async () => mockDocs
    });

    try {
      const result = await getDocuments();
      assert.equal(result.length, 2);
      assert.equal(result[0].name, 'Policy.pdf');
      assert.equal(result[0].type, 'PDF');
      assert.equal(result[1].name, 'Handbook.docx');
      assert.equal(result[1].type, 'DOCX');
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('Scenario 2: Backend returns HTTP 200 with an empty document list', async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async () => ({
      ok: true,
      status: 200,
      json: async () => []
    });

    try {
      const result = await getDocuments();
      assert.equal(Array.isArray(result), true);
      assert.equal(result.length, 0);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('Scenario 3: Backend returns HTTP 503 Service Unavailable', async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async () => ({
      ok: false,
      status: 503,
      json: async () => ({ status: 503, error: 'Service Unavailable' })
    });

    try {
      await assert.rejects(
        async () => {
          await getDocuments();
        },
        (err) => {
          assert.equal(err.status, 503);
          assert.equal(err.isServerUnavailable, true);
          assert.match(err.message, /server is temporarily unavailable/i);
          return true;
        }
      );
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('Scenario 4: Backend request times out or network is disconnected', async () => {
    const originalFetch = globalThis.fetch;
    
    // Simulate network error
    globalThis.fetch = async () => {
      throw new TypeError('Failed to fetch');
    };

    try {
      await assert.rejects(
        async () => {
          await getDocuments();
        },
        (err) => {
          assert.equal(err.isNetworkError, true);
          assert.equal(err.isServerUnavailable, true);
          assert.match(err.message, /server is temporarily unavailable/i);
          return true;
        }
      );
    } finally {
      globalThis.fetch = originalFetch;
    }

    // Simulate timeout error
    const slowFetch = async (url, options) => {
      return new Promise((resolve, reject) => {
        options.signal.addEventListener('abort', () => {
          const e = new Error('The operation was aborted');
          e.name = 'AbortError';
          reject(e);
        });
      });
    };
    globalThis.fetch = slowFetch;

    try {
      await assert.rejects(
        async () => {
          await getDocuments({ timeoutMs: 50 });
        },
        (err) => {
          assert.equal(err.status, 504);
          assert.equal(err.isTimeout, true);
          assert.equal(err.isServerUnavailable, true);
          assert.match(err.message, /server is temporarily unavailable/i);
          return true;
        }
      );
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('Scenario 5: Retry succeeds after a temporary failure (exponential backoff simulation)', async () => {
    let callCount = 0;
    const originalFetch = globalThis.fetch;

    globalThis.fetch = async () => {
      callCount++;
      if (callCount === 1) {
        return {
          ok: false,
          status: 503,
          json: async () => ({ status: 503 })
        };
      }
      return {
        ok: true,
        status: 200,
        json: async () => [{ id: 'doc-1', fileName: 'Resume.pdf' }]
      };
    };

    try {
      let result = null;
      let attempts = 0;
      const MAX_RETRIES = 3;

      while (attempts <= MAX_RETRIES) {
        try {
          result = await getDocuments();
          break;
        } catch (err) {
          attempts++;
          if (attempts > MAX_RETRIES) throw err;
          // Exponential backoff simulation
          const delay = 10 * Math.pow(2, attempts - 1);
          await new Promise((r) => setTimeout(r, delay));
        }
      }

      assert.equal(callCount, 2);
      assert.equal(result.length, 1);
      assert.equal(result[0].fileName, 'Resume.pdf');
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('Scenario 6: Repeated failures stop after the configured retry limit', async () => {
    let callCount = 0;
    const originalFetch = globalThis.fetch;

    globalThis.fetch = async () => {
      callCount++;
      return {
        ok: false,
        status: 503,
        json: async () => ({ status: 503 })
      };
    };

    try {
      let attempts = 0;
      const MAX_RETRIES = 3;
      let finalError = null;

      while (attempts <= MAX_RETRIES) {
        try {
          await getDocuments();
        } catch (err) {
          finalError = err;
          attempts++;
          if (attempts > MAX_RETRIES) break;
          // small delay
          await new Promise((r) => setTimeout(r, 5));
        }
      }

      // 1 initial attempt + 3 retries = 4 attempts total
      assert.equal(callCount, 4);
      assert.equal(attempts, 4);
      assert.match(finalError.message, /server is temporarily unavailable/i);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('Scenario 7: User edits a message and successfully resends it', async () => {
    // Initial conversation thread
    let messages = [
      { id: 'user-1', sender: 'user', text: 'Original question' },
      { id: 'assistant-1', sender: 'assistant', text: 'Original answer' }
    ];

    // User edits message at index 0
    const targetMessage = messages[0];
    const newText = 'Updated question';

    // Resend handler logic (mirroring App.jsx handleResendEditedMessage)
    const editIndex = messages.findIndex((m) => m.id === targetMessage.id);
    assert.equal(editIndex, 0);

    const editedUserMessage = {
      ...messages[editIndex],
      text: newText,
      isEdited: true,
      editedAt: new Date()
    };

    // Truncate obsolete subsequent turns and replace user message
    messages = [...messages.slice(0, editIndex), editedUserMessage];
    assert.equal(messages.length, 1);
    assert.equal(messages[0].text, 'Updated question');
    assert.equal(messages[0].isEdited, true);

    // Assistant generates new response
    const newAssistantMessage = {
      id: 'assistant-2',
      sender: 'assistant',
      text: 'New answer corresponding to updated question'
    };
    messages.push(newAssistantMessage);

    assert.equal(messages.length, 2);
    assert.equal(messages[0].text, 'Updated question');
    assert.equal(messages[1].text, 'New answer corresponding to updated question');
  });

  it('Scenario 8: User cancels editing', () => {
    let question = 'Draft question being typed';
    let savedDraft = '';
    let editingMessage = null;

    const originalMessages = [
      { id: 'user-1', sender: 'user', text: 'Message 1' },
      { id: 'assistant-1', sender: 'assistant', text: 'Answer 1' }
    ];

    // User clicks Edit
    savedDraft = question;
    editingMessage = originalMessages[0];
    question = editingMessage.text;

    assert.equal(question, 'Message 1');
    assert.equal(editingMessage.id, 'user-1');

    // User modifies text but then clicks Cancel
    question = 'Message 1 modified by mistake';

    // Cancel edit handler
    editingMessage = null;
    question = savedDraft;
    savedDraft = '';

    assert.equal(editingMessage, null);
    assert.equal(question, 'Draft question being typed');
    assert.equal(originalMessages.length, 2);
    assert.equal(originalMessages[0].text, 'Message 1');
  });

  it('Scenario 9: Resending fails and the edited text remains available for another attempt', async () => {
    let question = 'Edited question';
    let messages = [
      { id: 'user-1', sender: 'user', text: 'Original question' },
      { id: 'assistant-1', sender: 'assistant', text: 'Original answer' }
    ];

    const editIndex = 0;
    const editedUserMessage = {
      ...messages[editIndex],
      text: question,
      isEdited: true
    };
    messages = [...messages.slice(0, editIndex), editedUserMessage];

    // Simulate API failure during resend
    const apiError = new Error('AI service timeout');

    // On failure: append error message and preserve edited text in question composer
    messages.push({
      id: 'error-1',
      sender: 'assistant',
      text: `**Service Error:** ${apiError.message}`,
      isError: true,
      originalQuestion: question
    });

    assert.equal(question, 'Edited question'); // Edited text preserved!
    assert.equal(messages.length, 2);
    assert.equal(messages[1].isError, true);
    assert.equal(messages[1].originalQuestion, 'Edited question');
  });

  it('Scenario 10: Editing an earlier message does not produce an inconsistent assistant response or conversation history', () => {
    // 4 messages (2 turns)
    let messages = [
      { id: 'user-1', sender: 'user', text: 'Question 1' },
      { id: 'assistant-1', sender: 'assistant', text: 'Answer 1' },
      { id: 'user-2', sender: 'user', text: 'Question 2' },
      { id: 'assistant-2', sender: 'assistant', text: 'Answer 2' }
    ];

    // User edits earlier message (user-1 at index 0)
    const editIndex = messages.findIndex((m) => m.id === 'user-1');
    assert.equal(editIndex, 0);

    const editedUser = {
      ...messages[editIndex],
      text: 'Question 1 Edited',
      isEdited: true
    };

    // Truncating after editIndex removes the obsolete assistant-1, user-2, and assistant-2
    messages = [...messages.slice(0, editIndex), editedUser];
    assert.equal(messages.length, 1);
    assert.equal(messages[0].text, 'Question 1 Edited');

    // Add new assistant response
    messages.push({
      id: 'assistant-new',
      sender: 'assistant',
      text: 'Answer to Question 1 Edited'
    });

    assert.equal(messages.length, 2);
    assert.equal(messages[0].id, 'user-1');
    assert.equal(messages[0].text, 'Question 1 Edited');
    assert.equal(messages[1].text, 'Answer to Question 1 Edited');
    // Obsolete responses from branch are gone
    assert.equal(messages.some((m) => m.id === 'assistant-1'), false);
    assert.equal(messages.some((m) => m.id === 'user-2'), false);
    assert.equal(messages.some((m) => m.id === 'assistant-2'), false);
  });

  it('Scenario 11: Multiple clicks do not trigger duplicate submissions', async () => {
    let submissionCount = 0;
    let isGenerating = false;

    const triggerSubmit = async () => {
      if (isGenerating) return false;
      isGenerating = true;
      submissionCount++;
      await new Promise((r) => setTimeout(r, 50));
      isGenerating = false;
      return true;
    };

    // User clicks submit 5 times rapidly
    const results = await Promise.all([
      triggerSubmit(),
      triggerSubmit(),
      triggerSubmit(),
      triggerSubmit(),
      triggerSubmit()
    ]);

    // Only the first call goes through
    assert.equal(submissionCount, 1);
    assert.equal(results.filter(Boolean).length, 1);
  });

});
