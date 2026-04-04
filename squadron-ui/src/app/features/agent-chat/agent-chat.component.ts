import { Component, inject, OnInit, OnDestroy, signal, ElementRef, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { Subscription } from 'rxjs';
import { AgentService, AgentSession, AgentMessage } from '../../core/services/agent.service';
import { WebSocketService, ConnectionState } from '../../core/services/websocket.service';
import { StreamChunk, ChatRequest, AgentProgress } from '../../core/models/agent.model';
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

  /** Agent progress/TODO tracking. */
  progress = signal<AgentProgress | null>(null);

  /** Whether the agent was interrupted. */
  interrupted = signal(false);

  /** Tracks whether we're in streaming mode (WebSocket connected). */
  private streamingSub: Subscription | null = null;
  private progressSub: Subscription | null = null;
  private conversationId: string | null = null;
  private taskId: string | null = null;

  @ViewChild('chatBody') chatBody!: ElementRef;

  /** Progress bar percentage (0..1). */
  get progressPercent(): number {
    const p = this.progress();
    if (!p || p.totalSteps === 0) return 0;
    return p.completedSteps / p.totalSteps;
  }

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
        error: (err) => {
          console.error('Failed to load agent session:', err);
          this.loading.set(false);
          this.messages.set([]);
          this.progress.set(null);
        },
      });
    }
  }

  ngOnDestroy(): void {
    this.streamingSub?.unsubscribe();
    this.progressSub?.unsubscribe();
    if (this.conversationId) {
      this.wsService.unsubscribe(`/topic/chat/${this.conversationId}`);
      this.wsService.unsubscribe(`/topic/progress/${this.conversationId}`);
    }
  }

  sendMessage(): void {
    if (!this.newMessage.trim() || this.sending()) return;
    const sess = this.session();
    const content = this.newMessage.trim();
    this.newMessage = '';
    this.sending.set(true);
    this.interrupted.set(false);

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
      // No session available — cannot send message
      this.sending.set(false);
    }
  }

  /** Cancel/interrupt the currently running agent. */
  cancelAgent(): void {
    if (!this.conversationId) return;

    // Prefer WebSocket if connected, fallback to REST
    if (this.wsService.connectionState() === 'connected') {
      this.agentService.sendInterruptMessage(
        { conversationId: this.conversationId, reason: 'USER_CANCEL' },
        this.wsService,
      );
    } else {
      this.agentService.interruptAgent(this.conversationId, 'USER_CANCEL').subscribe();
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

    // Subscribe to progress updates
    this.progressSub = this.agentService.subscribeToProgress(conversationId, this.wsService)
      .subscribe({
        next: (progress: AgentProgress) => this.progress.set(progress),
        error: (err) => console.error('Progress subscription error:', err),
      });
  }

  /** Handle an individual streaming chunk from the WebSocket. */
  handleStreamChunk(chunk: StreamChunk): void {
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

      case 'interrupted': {
        // Agent was interrupted/cancelled
        const partialContent = this.streamingContent();
        if (partialContent) {
          const partialMsg: AgentMessage = {
            id: chunk.messageId || crypto.randomUUID(),
            sessionId: this.conversationId || 'unknown',
            role: 'AGENT',
            content: partialContent,
            tokenUsage: chunk.tokenCount,
            createdAt: new Date().toISOString(),
          };
          this.messages.update((msgs) => [...msgs, partialMsg]);
        }
        this.streamingContent.set('');
        this.sending.set(false);
        this.interrupted.set(true);
        const interruptMsg: AgentMessage = {
          id: crypto.randomUUID(),
          sessionId: this.conversationId || 'unknown',
          role: 'SYSTEM',
          content: chunk.content || 'Agent was interrupted by user.',
          createdAt: new Date().toISOString(),
        };
        this.messages.update((msgs) => [...msgs, interruptMsg]);
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
