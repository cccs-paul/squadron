import { Component, inject, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { TaskService } from '../../../core/services/task.service';
import { AgentService, AgentSession } from '../../../core/services/agent.service';
import { UserSquadronService } from '../../../core/services/user-squadron.service';
import { WebSocketService } from '../../../core/services/websocket.service';
import { Task, TaskState, TaskPriority } from '../../../core/models/task.model';
import { ConversationSummary, AgentProgress } from '../../../core/models/agent.model';
import { UserAgentConfig } from '../../../core/models/squadron-config.model';
import { AvatarComponent } from '../../../shared/components/avatar/avatar.component';
import { TimeAgoPipe } from '../../../shared/pipes/time-ago.pipe';

@Component({
  selector: 'sq-task-detail',
  standalone: true,
  imports: [RouterLink, AvatarComponent, TimeAgoPipe, DecimalPipe, TranslateModule],
  templateUrl: './task-detail.component.html',
  styleUrl: './task-detail.component.scss',
})
export class TaskDetailComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private taskService = inject(TaskService);
  private agentService = inject(AgentService);
  private userSquadronService = inject(UserSquadronService);
  private wsService = inject(WebSocketService);

  task = signal<Task | null>(null);
  loading = signal(true);

  /** Conversation summaries for this task. */
  conversationSummaries = signal<ConversationSummary[]>([]);
  /** User's agent squadron for the "change agent" selector. */
  squadronAgents = signal<UserAgentConfig[]>([]);
  /** Active agent session for this task (if any). */
  agentSession = signal<AgentSession | null>(null);
  /** Live agent progress. */
  agentProgress = signal<AgentProgress | null>(null);
  /** Whether the agent selector dropdown is open. */
  showAgentSelector = signal(false);

  private progressSub: Subscription | null = null;
  private streamSub: Subscription | null = null;

  /** The most recent active conversation (if any). */
  activeConversation = computed(() => {
    const summaries = this.conversationSummaries();
    return summaries.find(c => c.status === 'ACTIVE') ?? null;
  });

  /** Progress bar percentage (0..1). */
  progressPercent = computed(() => {
    const p = this.agentProgress();
    if (!p || p.totalSteps === 0) return 0;
    return p.completedSteps / p.totalSteps;
  });

  readonly stateTransitions: Record<string, TaskState[]> = {
    BACKLOG: [TaskState.PLANNING],
    PLANNING: [TaskState.IN_PROGRESS, TaskState.BACKLOG],
    IN_PROGRESS: [TaskState.REVIEW, TaskState.PLANNING],
    REVIEW: [TaskState.QA, TaskState.IN_PROGRESS],
    QA: [TaskState.DONE, TaskState.IN_PROGRESS],
    DONE: [],
  };

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.taskService.getTask(id).subscribe({
        next: (task) => {
          this.task.set(task);
          this.loading.set(false);
          this.loadAgentData(id);
        },
        error: (err) => {
          console.error('Failed to load task:', err);
          this.task.set(null);
          this.loading.set(false);
        },
      });
    }
  }

  ngOnDestroy(): void {
    this.progressSub?.unsubscribe();
    this.streamSub?.unsubscribe();
  }

  /** Load conversation summaries, agent session, and squadron agents. */
  private loadAgentData(taskId: string): void {
    // Load conversation summaries
    this.agentService.getConversationSummaries(taskId).subscribe({
      next: (summaries) => this.conversationSummaries.set(summaries),
      error: () => this.conversationSummaries.set([]),
    });

    // Load active agent session
    this.agentService.getSession(taskId).subscribe({
      next: (session) => {
        this.agentSession.set(session);
        if (session.status === 'ACTIVE') {
          this.subscribeToProgress(session.id);
        }
      },
      error: () => this.agentSession.set(null),
    });

    // Load user's squadron for agent selector
    this.userSquadronService.getMySquadron().subscribe({
      next: (agents) => this.squadronAgents.set(agents.filter(a => a.enabled)),
      error: () => this.squadronAgents.set([]),
    });
  }

  /** Subscribe to live progress updates via WebSocket. */
  private subscribeToProgress(conversationId: string): void {
    this.wsService.connect();
    this.progressSub = this.agentService.subscribeToProgress(conversationId, this.wsService)
      .subscribe({
        next: (progress) => this.agentProgress.set(progress),
        error: (err) => console.error('Progress subscription error:', err),
      });
  }

  transitionTo(state: TaskState): void {
    const current = this.task();
    if (!current) return;
    this.taskService.transitionTask(current.id, state).subscribe({
      next: (updated) => this.task.set(updated),
      error: () => this.task.update((t) => t ? { ...t, state } : t),
    });
  }

  getAvailableTransitions(): TaskState[] {
    const t = this.task();
    if (!t) return [];
    return this.stateTransitions[t.state] || [];
  }

  /** Navigate to the full agent chat view. */
  openAgentChat(): void {
    const t = this.task();
    if (t) {
      this.router.navigate(['/agent', t.id]);
    }
  }

  /** Toggle the agent selector dropdown. */
  toggleAgentSelector(): void {
    this.showAgentSelector.update(v => !v);
  }

  /** Get the label for the currently assigned agent type from squadron. */
  getAssignedAgentLabel(): string {
    const session = this.agentSession();
    if (!session) return '';
    const agents = this.squadronAgents();
    const match = agents.find(a => a.agentName === session.currentPlan?.steps?.[0]?.description);
    return match?.agentName ?? session.id.substring(0, 8);
  }

  priorityClass(priority: TaskPriority): string {
    switch (priority) {
      case TaskPriority.CRITICAL: return 'error';
      case TaskPriority.HIGH: return 'warning';
      case TaskPriority.MEDIUM: return 'primary';
      default: return 'neutral';
    }
  }

  stateColor(state: TaskState): string {
    const colors: Record<string, string> = {
      BACKLOG: '#9CA3AF', PLANNING: '#818CF8', IN_PROGRESS: '#06B6D4',
      REVIEW: '#F59E0B', QA: '#8B5CF6', DONE: '#10B981',
    };
    return colors[state] || '#9CA3AF';
  }

  /** Get a CSS class for conversation status. */
  conversationStatusClass(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'status--active';
      case 'COMPLETED': return 'status--completed';
      case 'FAILED': return 'status--failed';
      default: return 'status--default';
    }
  }
}
