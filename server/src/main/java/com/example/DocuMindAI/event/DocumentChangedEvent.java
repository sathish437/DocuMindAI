package com.example.DocuMindAI.event;

import java.util.UUID;

/**
 * Event fired when a document is uploaded, updated, or deleted,
 * allowing caches to be invalidated cleanly without tight coupling.
 */
public record DocumentChangedEvent(UUID documentId, String action) {
}
