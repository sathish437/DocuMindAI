import React from 'react';
import { MenuIcon, PlusIcon, LogoMark, SidebarIcon } from './Icons';

export function Header({
  sidebarOpen,
  onToggleSidebar,
  onNewChat,
  documentCount = 0,
  activeTitle = 'Document Intelligence Workspace'
}) {
  return (
    <header className="app-header" id="main-app-header">
      <div className="header-left">
        <button
          type="button"
          id="header-sidebar-toggle"
          className="header-icon-btn mobile-menu-btn"
          onClick={onToggleSidebar}
          aria-label={sidebarOpen ? 'Collapse sidebar' : 'Expand sidebar'}
          title={sidebarOpen ? 'Collapse sidebar' : 'Expand sidebar'}
        >
          {sidebarOpen ? <SidebarIcon size={18} /> : <MenuIcon size={18} />}
        </button>

        {!sidebarOpen && (
          <div className="header-branding-compact">
            <LogoMark size={22} />
            <span className="header-brand-name">DocuMindAI</span>
          </div>
        )}

        {!sidebarOpen && <div className="header-divider" />}

        <div className="header-title-block">
          <h2 className="header-workspace-title">{activeTitle}</h2>
          {documentCount > 0 ? (
            <span className="header-doc-badge" title={`${documentCount} document(s) indexed for search`}>
              <span className="badge-dot" />
              {documentCount} {documentCount === 1 ? 'doc' : 'docs'}
            </span>
          ) : (
            <span className="header-doc-badge empty" title="No documents indexed yet">
              0 docs
            </span>
          )}
        </div>
      </div>

      <div className="header-right">
        <button
          type="button"
          id="header-new-chat-btn"
          className="header-new-chat-btn"
          onClick={onNewChat}
          title="Start fresh conversation"
          aria-label="New chat"
        >
          <PlusIcon size={14} />
          <span className="new-chat-label">New Chat</span>
        </button>
      </div>
    </header>
  );
}

export default Header;
