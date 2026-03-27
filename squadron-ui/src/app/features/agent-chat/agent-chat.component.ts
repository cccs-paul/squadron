import { Component, inject, OnInit, OnDestroy, signal, ElementRef, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { Subscription } from 'rxjs';
import { AgentService, AgentSession, AgentMessage } from '../../core/services/agent.service';
import { WebSocketService, ConnectionState } from '../../core/services/websocket.service';
import { StreamChunk, ChatRequest } from '../../core/models/agent.model';
import { AvatarComponent } from '../../shared/components/avatar/avatar.component';
import { TimeAgoPipe } from '../../shared/pipes/time-ago.pipe';

@Component({
  selector: 'sq-agent-chat',
  standalone: true,
  imports: [FormsModule, DecimalPipe, AvatarComponent, TimeAgoPipe],
  templateUrl: './agent-chat.component.html',
  styleUrl: './agent-chat.component.scss',
})
export class AgentChatComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private agentService = inject(AgentService);
  private wsService = inject(WebSocketService);

  session = signal<AgentSession | null>(null);
  messages = signal<AgentMessage[]>([]);
  newMessage = '';
  sending = signal(false);
  loading = signal(true);
  streamingContent = signal('');
  connectionState = signal<ConnectionState>('disconnected');

  /** Tracks whether we're in streaming mode (WebSocket connected). */
  private streamingSub: Subscription | null = null;
  private conversationId: string | null = null;
  private taskId: string | null = null;

  @ViewChild('chatBody') chatBody!: ElementRef;

  ngOnInit(): void {
    this.taskId = this.route.snapshot.paramMap.get('taskId');
    if (this.taskId) {
      this.agentService.getSession(this.taskId).subscribe({
        next: (session) => {
          this.session.set(session);
          this.messages.set(session.messages || []);
          this.loading.set(false);
          // If session has an id, use it as conversationId and connect to WebSocket
          this.conversationId = session.id;
          this.connectAndSubscribe(session.id);
        },
        error: () => {
          this.loading.set(false);
          this.messages.set([
            { id: '1', sessionId: 's1', role: 'SYSTEM', content: 'Agent session started for task SQ-42', createdAt: new Date(Date.now() - 3600000).toISOString() },
            { id: '2', sessionId: 's1', role: 'AGENT', content: 'I\'ve analyzed the task requirements. Here\'s my plan:\n\n1. **Create dashboard layout** - Set up the main grid structure\n2. **Implement stat cards** - Build the overview statistics components\n3. **Add activity feed** - Create the recent activity timeline\n4. **Build chart component** - Implement the task distribution bar chart\n\nShall I proceed with this plan?', createdAt: new Date(Date.now() - 3500000).toISOString(), tokenUsage: 1250 },
            { id: '3', sessionId: 's1', role: 'USER', content: 'Yes, proceed with the plan. Make sure to use responsive design.', createdAt: new Date(Date.now() - 3400000).toISOString() },
            { id: '4', sessionId: 's1', role: 'AGENT', content: 'Starting implementation now. I\'ll begin with the dashboard layout component.\n\n```typescript\n@Component({\n  selector: \'sq-dashboard\',\n  template: `\n    <div class="dashboard">\n      <div class="stats-grid">...</div>\n      <div class="dashboard-grid">...</div>\n    </div>\n  `\n})\nexport class DashboardComponent {\n  // Implementation\n}\n```\n\nThe stat cards are complete. Moving on to the activity feed...', createdAt: new Date(Date.now() - 3000000).toISOString(), tokenUsage: 4500 },
          ]);
        },
      });
    }
  }

  ngOnDestroy(): void {
    this.streamingSub?.unsubscribe();
    if (this.conversationId) {
      this.wsService.unsubscribe(`/topic/chat/${this.conversationId}`);
    }
    // Don't disconnect the shared service — other components may use it
  }

  sendMessage(): void {
    if (!this.newMessage.trim() || this.sending()) return;
    const sess = this.session();
    const content = this.newMessage.trim();
    this.newMessage = '';
    this.sending.set(true);

    const userMsg: AgentMessage = {
      id: crypto.randomUUID(), sessionId: sess?.id || 'mock', role: 'USER',
      content, createdAt: new Date().toISOString(),
    };
    this.messages.update((msgs) => [...msgs, userMsg]);
    this.scrollToBottom();

    // If we have a WebSocket connection and conversationId, use streaming
    if (this.conversationId && this.wsService.connectionState() === 'connected') {
      this.streamingContent.set('');
      const request: ChatRequest = {
        conversationId: this.conversationId,
        taskId: this.taskId!,
        agentType: 'CODING',
        message: content,
      };
      this.agentService.sendStreamingMessage(request, this.wsService);
      // Response will arrive via the STOMP subscription set up in connectAndSubscribe()
    } else if (sess) {
      // Fallback to HTTP request/response
      this.agentService.sendMessage(sess.id, content).subscribe({
        next: (response) => {
          this.messages.update((msgs) => [...msgs, response]);
          this.sending.set(false);
          this.scrollToBottom();
        },
        error: () => this.sending.set(false),
      });
    } else {
      // Mock mode — no backend
      setTimeout(() => {
        this.messages.update((msgs) => [...msgs, {
          id: crypto.randomUUID(), sessionId: 'mock', role: 'AGENT' as const,
          content: 'I received your message. Processing...', createdAt: new Date().toISOString(), tokenUsage: 150,
        }]);
        this.sending.set(false);
        this.scrollToBottom();
      }, 1000);
    }
  }

  /** Connect to WebSocket and subscribe to the conversation's streaming topic. */
  private connectAndSubscribe(conversationId: string): void {
    this.wsService.connect();
    this.connectionState.set(this.wsService.connectionState());

    this.streamingSub = this.agentService.subscribeToStream(conversationId, this.wsService)
      .subscribe({
        next: (chunk: StreamChunk) => this.handleStreamChunk(chunk),
        error: (err) => console.error('Stream subscription error:', err),
      });
  }

  /** Handle an individual streaming chunk from the WebSocket. */
  private handleStreamChunk(chunk: StreamChunk): void {
    switch (chunk.type) {
      case 'chunk':
        this.streamingContent.update((c) => c + (chunk.content || ''));
        this.scrollToBottom();
        break;

      case 'done': {
        // Streaming complete — add the full response as a message
        const fullContent = this.streamingContent();
        if (fullContent) {
          const agentMsg: AgentMessage = {
            id: chunk.messageId || crypto.randomUUID(),
            sessionId: this.conversationId || 'unknown',
            role: 'AGENT',
            content: fullContent,
            tokenUsage: chunk.tokenCount,
            createdAt: new Date().toISOString(),
          };
          this.messages.update((msgs) => [...msgs, agentMsg]);
        }
        this.streamingContent.set('');
        this.sending.set(false);
        this.scrollToBottom();
        break;
      }

      case 'error':
        this.streamingContent.set('');
        this.sending.set(false);
        const errorMsg: AgentMessage = {
          id: crypto.randomUUID(),
          sessionId: this.conversationId || 'unknown',
          role: 'SYSTEM',
          content: `Error: ${chunk.content || 'Unknown error occurred'}`,
          createdAt: new Date().toISOString(),
        };
        this.messages.update((msgs) => [...msgs, errorMsg]);
        this.scrollToBottom();
        break;
    }
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      if (this.chatBody?.nativeElement) {
        this.chatBody.nativeElement.scrollTop = this.chatBody.nativeElement.scrollHeight;
      }
    }, 50);
  }

  formatCodeBlocks(content: string): string {
    return content
      .replace(/```(\w+)?\n([\s\S]*?)```/g, '<pre><code class="lang-$1">$2</code></pre>')
      .replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/\n/g, '<br>');
  }
}
