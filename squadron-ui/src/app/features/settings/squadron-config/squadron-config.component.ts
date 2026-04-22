import { Component, ElementRef, inject, OnInit, OnDestroy, signal, ViewChildren, QueryList } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { UserSquadronService } from '../../../core/services/user-squadron.service';
import { AgentTestService } from '../../../core/services/agent-test.service';
import {
  UserAgentConfig,
  HostingType,
  PROVIDER_CATALOG,
  ProviderCatalogEntry,
  ModelCatalogEntry,
  generateAgentDescription,
} from '../../../core/models/squadron-config.model';
import {
  TestMode,
  TEST_MODE_LABELS,
  AgentTestResult,
  TestLogEntry,
  InteractiveTestSession,
  InteractiveTestMessage,
} from '../../../core/models/agent-test.model';

@Component({
  selector: 'sq-squadron-config',
  standalone: true,
  imports: [FormsModule, TranslateModule],
  template: `
    <div class="squadron-config">
      <div class="squadron-config__header">
        <h2 class="squadron-config__title">{{ 'settings.squadronConfig.title' | translate }}</h2>
        <p class="squadron-config__subtitle">
          {{ 'settings.squadronConfig.subtitle' | translate:{ maxAgents: maxAgents() } }}
        </p>
      </div>

      @if (loading()) {
        <div class="squadron-config__loading">{{ 'settings.squadronConfig.loading' | translate }}</div>
      } @else {
        @if (saveSuccess()) {
          <div class="squadron-config__message squadron-config__message--success">
            {{ 'settings.squadronConfig.saveSuccess' | translate }}
          </div>
        }
        @if (saveError()) {
          <div class="squadron-config__message squadron-config__message--error">
            {{ saveError() }}
          </div>
        }

        <div class="squadron-config__agents">
          @for (agent of agents(); track agent.id) {
            <div class="squadron-config__agent-card">
              <div class="squadron-config__agent-header">
                <div class="squadron-config__agent-info">
                  @if (editingId() === agent.id) {
                    <input
                      class="squadron-config__input squadron-config__input--name"
                      [(ngModel)]="editName"
                      [placeholder]="'settings.squadronConfig.agentName' | translate"
                    />
                  } @else {
                    <span class="squadron-config__agent-name">{{ agent.agentName }}</span>
                    @if (agent.description) {
                      <span class="squadron-config__agent-description">{{ agent.description }}</span>
                    }
                    @if (agent.hostingType) {
                      <span class="squadron-config__hosting-badge"
                            [class.squadron-config__hosting-badge--platform]="agent.hostingType === 'PLATFORM'"
                            [class.squadron-config__hosting-badge--self-hosted]="agent.hostingType === 'SELF_HOSTED'"
                            [class.squadron-config__hosting-badge--custom]="agent.hostingType === 'CUSTOM'">
                        {{ getHostingLabel(agent.hostingType) }}
                      </span>
                    }
                  }
                </div>
                <div class="squadron-config__agent-actions">
                  @if (editingId() === agent.id) {
                    <button class="squadron-config__btn squadron-config__btn--save" (click)="saveAgent(agent)"
                      [disabled]="saving()">{{ 'settings.squadronConfig.save' | translate }}</button>
                    <button class="squadron-config__btn squadron-config__btn--cancel" (click)="cancelEdit()">{{ 'common.cancel' | translate }}</button>
                  } @else {
                    <button class="squadron-config__btn squadron-config__btn--test" (click)="openTestMenu(agent)"
                      [disabled]="testingAgentIds().has(agent.id!)">
                      {{ 'settings.squadronConfig.test' | translate }}
                    </button>
                    <button class="squadron-config__btn squadron-config__btn--edit" (click)="startEdit(agent)">{{ 'settings.squadronConfig.edit' | translate }}</button>
                    <button class="squadron-config__btn squadron-config__btn--remove" (click)="removeAgent(agent)"
                      [disabled]="agents().length <= 1">{{ 'settings.squadronConfig.remove' | translate }}</button>
                  }
                </div>
              </div>

              <!-- Test mode selector (shows when test menu is open for this agent) -->
              @if (testMenuAgentId() === agent.id && !testingAgentIds().has(agent.id!)) {
                <div class="squadron-config__test-menu">
                  <span class="squadron-config__test-menu-label">{{ 'settings.squadronConfig.selectTestMode' | translate }}</span>
                  <div class="squadron-config__test-menu-buttons">
                    <button class="squadron-config__btn squadron-config__btn--test-mode"
                      (click)="runTest(agent, 'PLANNING')">
                      {{ 'settings.squadronConfig.testModes.planning' | translate }}
                    </button>
                    <button class="squadron-config__btn squadron-config__btn--test-mode"
                      (click)="runTest(agent, 'CODE_GENERATION')">
                      {{ 'settings.squadronConfig.testModes.codeGeneration' | translate }}
                    </button>
                    <button class="squadron-config__btn squadron-config__btn--test-mode"
                      (click)="runTest(agent, 'CODE_REVIEW')">
                      {{ 'settings.squadronConfig.testModes.codeReview' | translate }}
                    </button>
                    <button class="squadron-config__btn squadron-config__btn--test-mode squadron-config__btn--interactive"
                      (click)="startInteractiveTest(agent)">
                      {{ 'settings.squadronConfig.testModes.interactive' | translate }}
                    </button>
                    <button class="squadron-config__btn squadron-config__btn--cancel"
                      (click)="closeTestMenu()">
                      {{ 'common.cancel' | translate }}
                    </button>
                  </div>
                </div>
              }

              <!-- Expandable test result panel -->
              @if (testResultForAgent(agent.id!); as result) {
                <div class="squadron-config__test-panel">
                  <div class="squadron-config__test-panel-header" (click)="toggleTestExpanded(agent.id!)">
                    <div class="squadron-config__test-status"
                         [class.squadron-config__test-status--running]="result.status === 'RUNNING'"
                         [class.squadron-config__test-status--success]="result.status === 'SUCCESS'"
                         [class.squadron-config__test-status--failure]="result.status === 'FAILURE' || result.status === 'ERROR'">
                      @if (result.status === 'RUNNING') {
                        <span class="squadron-config__spinner"></span>
                      }
                      {{ result.status }}
                    </div>
                    <span class="squadron-config__test-summary">{{ result.summary }}</span>
                    @if (result.durationMs) {
                      <span class="squadron-config__test-duration">{{ result.durationMs }}ms</span>
                    }
                    <span class="squadron-config__test-expand-icon">
                      {{ isTestExpanded(agent.id!) ? '&#9660;' : '&#9654;' }}
                    </span>
                  </div>

                  @if (isTestExpanded(agent.id!)) {
                    <div class="squadron-config__test-panel-body">
                      <!-- Verbose log entries -->
                      <div class="squadron-config__test-log">
                        @for (entry of result.logEntries; track $index) {
                          <div class="squadron-config__test-log-entry"
                               [class.squadron-config__test-log-entry--success]="entry.level === 'SUCCESS'"
                               [class.squadron-config__test-log-entry--warning]="entry.level === 'WARNING'"
                               [class.squadron-config__test-log-entry--error]="entry.level === 'ERROR'">
                            <span class="squadron-config__test-log-phase">{{ entry.phase }}</span>
                            <span class="squadron-config__test-log-message">{{ entry.message }}</span>
                          </div>
                        }
                      </div>

                      <!-- Agent output (collapsed by default, expandable) -->
                      @if (result.agentOutput) {
                        <div class="squadron-config__test-output">
                          <div class="squadron-config__test-output-header" (click)="toggleOutputExpanded(agent.id!)">
                            <span>{{ 'settings.squadronConfig.agentOutput' | translate }}</span>
                            <span>{{ isOutputExpanded(agent.id!) ? '&#9660;' : '&#9654;' }}</span>
                          </div>
                          @if (isOutputExpanded(agent.id!)) {
                            <pre class="squadron-config__test-output-content">{{ result.agentOutput }}</pre>
                          }
                        </div>
                      }

                      <button class="squadron-config__btn squadron-config__btn--dismiss"
                        (click)="dismissTestResult(agent.id!)">
                        {{ 'settings.squadronConfig.dismissResult' | translate }}
                      </button>
                    </div>
                  }
                </div>
              }

              <!-- Interactive chat panel (inline on the agent card) -->
              @if (interactiveSessionForAgent(agent.id!); as chatSession) {
                <div class="squadron-config__chat-panel">
                  <div class="squadron-config__chat-header">
                    <div class="squadron-config__chat-title">
                      <span class="squadron-config__chat-agent-name">{{ chatSession.agentName }}</span>
                      <span class="squadron-config__chat-model">{{ chatSession.provider }}/{{ chatSession.model }}</span>
                      @if (chatSession.containerId) {
                        <span class="squadron-config__chat-container-badge"
                          title="Running in ephemeral container {{ chatSession.containerId }}">
                          {{ chatSession.containerId }}
                        </span>
                      }
                      @if (chatSession.status === 'STREAMING') {
                        <span class="squadron-config__chat-streaming-badge">
                          {{ 'settings.squadronConfig.interactive.streaming' | translate }}
                        </span>
                      }
                    </div>
                    <button class="squadron-config__btn squadron-config__btn--close-chat"
                      (click)="closeInteractiveSession(agent.id!)">
                      {{ 'settings.squadronConfig.interactive.close' | translate }}
                    </button>
                  </div>

                  <div class="squadron-config__chat-body" [attr.data-agent-id]="agent.id">
                    @for (msg of chatSession.messages; track msg.id) {
                      <div class="squadron-config__chat-message"
                           [class.squadron-config__chat-message--user]="msg.role === 'USER'"
                           [class.squadron-config__chat-message--agent]="msg.role === 'AGENT'"
                           [class.squadron-config__chat-message--system]="msg.role === 'SYSTEM'">
                        <div class="squadron-config__chat-message-role">
                          @switch (msg.role) {
                            @case ('USER') { {{ 'settings.squadronConfig.interactive.youLabel' | translate }} }
                            @case ('AGENT') { {{ 'settings.squadronConfig.interactive.agentLabel' | translate }} }
                            @case ('SYSTEM') { {{ 'settings.squadronConfig.interactive.systemLabel' | translate }} }
                          }
                        </div>
                        <div class="squadron-config__chat-message-content" [innerHTML]="formatCodeBlocks(msg.content)"></div>
                        @if (msg.tokenCount) {
                          <span class="squadron-config__chat-message-tokens">{{ msg.tokenCount }} tokens</span>
                        }
                      </div>
                    }

                    @if (chatSession.status === 'STREAMING' && !hasStreamingAgentMessage(chatSession)) {
                      <div class="squadron-config__chat-message squadron-config__chat-message--agent">
                        <div class="squadron-config__chat-message-role">
                          {{ 'settings.squadronConfig.interactive.agentLabel' | translate }}
                        </div>
                        <div class="squadron-config__chat-typing">
                          <span></span><span></span><span></span>
                        </div>
                      </div>
                    }
                  </div>

                  <div class="squadron-config__chat-input">
                    <input
                      type="text"
                      class="squadron-config__input squadron-config__chat-input-field"
                      [placeholder]="'settings.squadronConfig.interactive.placeholder' | translate"
                      [value]="getChatInput(agent.id!)"
                      (input)="setChatInput(agent.id!, $any($event.target).value)"
                      (keyup.enter)="sendInteractiveMessage(agent.id!)"
                      [disabled]="chatSession.status === 'STREAMING'"
                    />
                    <button class="squadron-config__btn squadron-config__btn--send"
                      (click)="sendInteractiveMessage(agent.id!)"
                      [disabled]="chatSession.status === 'STREAMING' || !getChatInput(agent.id!).trim()">
                      {{ 'settings.squadronConfig.interactive.send' | translate }}
                    </button>
                  </div>
                </div>
              }

              @if (editingId() === agent.id) {
                <div class="squadron-config__agent-details">
                  <!-- Hosting Type -->
                  <div class="squadron-config__field">
                    <label class="squadron-config__label">{{ 'settings.squadronConfig.hostingType' | translate }}</label>
                    <select class="squadron-config__select" [(ngModel)]="editHostingType" (ngModelChange)="onHostingTypeChange()">
                      <option value="PLATFORM">{{ 'settings.squadronConfig.hostingTypes.platform' | translate }}</option>
                      <option value="SELF_HOSTED">{{ 'settings.squadronConfig.hostingTypes.selfHosted' | translate }}</option>
                      <option value="CUSTOM">{{ 'settings.squadronConfig.hostingTypes.custom' | translate }}</option>
                    </select>
                  </div>

                  <!-- Provider -->
                  <div class="squadron-config__field">
                    <label class="squadron-config__label">{{ 'settings.squadronConfig.provider' | translate }}</label>
                    <select class="squadron-config__select" [(ngModel)]="editProvider" (ngModelChange)="onProviderChange()">
                      <option value="">{{ 'settings.squadronConfig.selectProvider' | translate }}</option>
                      @for (p of filteredProviders(); track p.id) {
                        <option [value]="p.id">{{ p.label }}</option>
                      }
                      @if (editHostingType === 'CUSTOM') {
                        <option value="__custom__">{{ 'settings.squadronConfig.customProvider' | translate }}</option>
                      }
                    </select>
                  </div>

                  <!-- Model -->
                  <div class="squadron-config__field">
                    <label class="squadron-config__label">{{ 'settings.squadronConfig.model' | translate }}</label>
                    @if (availableModels().length > 0) {
                      <select class="squadron-config__select" [(ngModel)]="editModel">
                        <option value="">{{ 'settings.squadronConfig.selectModel' | translate }}</option>
                        @for (m of availableModels(); track m.id) {
                          <option [value]="m.id">{{ m.label }}</option>
                        }
                      </select>
                    } @else {
                      <input class="squadron-config__input" [(ngModel)]="editModel"
                             [placeholder]="'settings.squadronConfig.modelPlaceholder' | translate" />
                    }
                  </div>

                  <!-- Base URL (for self-hosted / custom / providers with default base URL) -->
                  @if (editHostingType === 'SELF_HOSTED' || editHostingType === 'CUSTOM' || selectedProviderEntry()?.defaultBaseUrl) {
                    <div class="squadron-config__field">
                      <label class="squadron-config__label">{{ 'settings.squadronConfig.baseUrl' | translate }}</label>
                      <input class="squadron-config__input" [(ngModel)]="editBaseUrl"
                             [placeholder]="selectedProviderEntry()?.defaultBaseUrl
                               ?? (editHostingType === 'SELF_HOSTED'
                                 ? 'http://localhost:11434'
                                 : 'https://api.example.com/v1')" />
                      @if (selectedProviderEntry()?.defaultBaseUrl) {
                        <span class="squadron-config__hint">{{ 'settings.squadronConfig.baseUrlHint' | translate:{ url: selectedProviderEntry()!.defaultBaseUrl } }}</span>
                      }
                    </div>
                  }

                  <!-- API Key (for custom endpoints or providers that require it) -->
                  @if (editHostingType === 'CUSTOM' || selectedProviderEntry()?.requiresApiKey) {
                    <div class="squadron-config__field">
                      <label class="squadron-config__label">{{ 'settings.squadronConfig.apiKey' | translate }}</label>
                      <input class="squadron-config__input" type="password" [(ngModel)]="editApiKeyRef"
                             [placeholder]="'settings.squadronConfig.apiKeyPlaceholder' | translate" />
                    </div>
                  }

                  <div class="squadron-config__field-row">
                    <div class="squadron-config__field">
                      <label class="squadron-config__label">{{ 'settings.squadronConfig.maxTokens' | translate }}</label>
                      <input class="squadron-config__input" type="number" [(ngModel)]="editMaxTokens" />
                    </div>
                    <div class="squadron-config__field">
                      <label class="squadron-config__label">{{ 'settings.squadronConfig.temperature' | translate }}</label>
                      <input class="squadron-config__input" type="number" step="0.1" min="0" max="2"
                        [(ngModel)]="editTemperature" />
                    </div>
                  </div>

                  <!-- Description (auto-generated or manual) -->
                  <div class="squadron-config__field">
                    <label class="squadron-config__label">{{ 'settings.squadronConfig.description' | translate }}</label>
                    <input class="squadron-config__input" [(ngModel)]="editDescription"
                           [placeholder]="autoDescription()" />
                    <span class="squadron-config__hint">{{ 'settings.squadronConfig.descriptionHint' | translate }}</span>
                  </div>

                  <div class="squadron-config__field">
                    <label class="squadron-config__label">{{ 'settings.squadronConfig.systemPrompt' | translate }}</label>
                    <textarea class="squadron-config__textarea" [(ngModel)]="editSystemPrompt"
                              rows="3" [placeholder]="'settings.squadronConfig.systemPromptPlaceholder' | translate"></textarea>
                  </div>

                  <div class="squadron-config__field">
                    <label class="squadron-config__label">
                      <input type="checkbox" [(ngModel)]="editEnabled" />
                      {{ 'settings.squadronConfig.enabled' | translate }}
                    </label>
                  </div>
                </div>
              }
            </div>
          }
        </div>

        <div class="squadron-config__footer">
          <button class="squadron-config__btn squadron-config__btn--add"
            (click)="addAgent()" [disabled]="agents().length >= maxAgents()">
            {{ 'settings.squadronConfig.addAgent' | translate }}
          </button>
          <button class="squadron-config__btn squadron-config__btn--reset" (click)="resetToDefaults()">
            {{ 'settings.squadronConfig.resetDefaults' | translate }}
          </button>
        </div>
      }
    </div>
  `,
  styles: [`
    .squadron-config { max-width: 800px; margin: 0 auto; padding: 24px; }
    .squadron-config__header { margin-bottom: 24px; }
    .squadron-config__title { font-size: 1.5rem; font-weight: 600; margin: 0 0 8px; }
    .squadron-config__subtitle { color: #6b7280; margin: 0; font-size: 0.875rem; }
    .squadron-config__loading { text-align: center; padding: 40px; color: #6b7280; }
    .squadron-config__message { padding: 12px 16px; border-radius: 6px; margin-bottom: 16px; font-size: 0.875rem; }
    .squadron-config__message--success { background: #d1fae5; color: #065f46; }
    .squadron-config__message--error { background: #fee2e2; color: #991b1b; }
    .squadron-config__agents { display: flex; flex-direction: column; gap: 12px; }
    .squadron-config__agent-card { border: 1px solid #e5e7eb; border-radius: 8px; padding: 16px; background: #fff; }
    .squadron-config__agent-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
    .squadron-config__agent-info { display: flex; flex-direction: column; gap: 4px; min-width: 0; flex: 1; }
    .squadron-config__agent-name { font-weight: 600; font-size: 1rem; }
    .squadron-config__agent-description { font-size: 0.8125rem; color: #6b7280; }
    .squadron-config__hosting-badge {
      display: inline-block; width: fit-content;
      font-size: 0.6875rem; font-weight: 600; padding: 2px 8px; border-radius: 9999px;
      text-transform: uppercase; letter-spacing: 0.025em;
    }
    .squadron-config__hosting-badge--platform { background: #ede9fe; color: #6d28d9; }
    .squadron-config__hosting-badge--self-hosted { background: #dbeafe; color: #1d4ed8; }
    .squadron-config__hosting-badge--custom { background: #fef3c7; color: #b45309; }
    .squadron-config__agent-actions { display: flex; gap: 8px; flex-shrink: 0; }
    .squadron-config__agent-details { margin-top: 16px; display: flex; flex-direction: column; gap: 12px;
      border-top: 1px solid #f3f4f6; padding-top: 16px; }
    .squadron-config__field { display: flex; flex-direction: column; gap: 4px; }
    .squadron-config__field-row { display: flex; gap: 16px; }
    .squadron-config__field-row .squadron-config__field { flex: 1; }
    .squadron-config__label { font-size: 0.75rem; font-weight: 500; color: #374151; }
    .squadron-config__hint { font-size: 0.6875rem; color: #9ca3af; }
    .squadron-config__input, .squadron-config__select {
      padding: 6px 10px; border: 1px solid #d1d5db; border-radius: 4px; font-size: 0.875rem;
    }
    .squadron-config__input--name { font-weight: 600; }
    .squadron-config__textarea {
      padding: 6px 10px; border: 1px solid #d1d5db; border-radius: 4px; font-size: 0.875rem;
      font-family: inherit; resize: vertical; min-height: 60px;
    }
    .squadron-config__btn {
      padding: 6px 14px; border: 1px solid #d1d5db; border-radius: 4px; background: #fff;
      cursor: pointer; font-size: 0.8125rem;
    }
    .squadron-config__btn:disabled { opacity: 0.5; cursor: not-allowed; }
    .squadron-config__btn--save { background: #2563eb; color: #fff; border-color: #2563eb; }
    .squadron-config__btn--cancel { background: #f3f4f6; }
    .squadron-config__btn--edit { background: #f9fafb; }
    .squadron-config__btn--remove { color: #dc2626; border-color: #fecaca; }
    .squadron-config__btn--add { background: #2563eb; color: #fff; border-color: #2563eb; }
    .squadron-config__btn--reset { background: #f3f4f6; }
    .squadron-config__btn--test { background: #f0fdf4; color: #15803d; border-color: #bbf7d0; font-weight: 500; }
    .squadron-config__btn--test:hover { background: #dcfce7; }
    .squadron-config__btn--test-mode { background: #f0fdf4; color: #15803d; border-color: #bbf7d0; }
    .squadron-config__btn--test-mode:hover { background: #dcfce7; }
    .squadron-config__btn--dismiss { background: #f3f4f6; font-size: 0.75rem; margin-top: 8px; }
    .squadron-config__footer { margin-top: 20px; display: flex; gap: 12px; }

    /* Test menu */
    .squadron-config__test-menu {
      margin-top: 12px; padding: 12px; background: #f0fdf4; border-radius: 6px;
      border: 1px solid #bbf7d0;
    }
    .squadron-config__test-menu-label { font-size: 0.75rem; font-weight: 500; color: #374151; display: block; margin-bottom: 8px; }
    .squadron-config__test-menu-buttons { display: flex; gap: 8px; flex-wrap: wrap; }

    /* Test result panel */
    .squadron-config__test-panel {
      margin-top: 12px; border: 1px solid #e5e7eb; border-radius: 6px; overflow: hidden;
    }
    .squadron-config__test-panel-header {
      display: flex; align-items: center; gap: 12px; padding: 10px 14px;
      background: #f9fafb; cursor: pointer; user-select: none;
    }
    .squadron-config__test-panel-header:hover { background: #f3f4f6; }
    .squadron-config__test-status {
      font-size: 0.6875rem; font-weight: 700; padding: 2px 8px; border-radius: 4px;
      text-transform: uppercase; letter-spacing: 0.05em; display: flex; align-items: center; gap: 6px;
    }
    .squadron-config__test-status--running { background: #dbeafe; color: #1d4ed8; }
    .squadron-config__test-status--success { background: #d1fae5; color: #065f46; }
    .squadron-config__test-status--failure { background: #fee2e2; color: #991b1b; }
    .squadron-config__test-summary { flex: 1; font-size: 0.8125rem; color: #374151; }
    .squadron-config__test-duration { font-size: 0.75rem; color: #6b7280; }
    .squadron-config__test-expand-icon { font-size: 0.75rem; color: #9ca3af; }
    .squadron-config__test-panel-body { padding: 14px; border-top: 1px solid #e5e7eb; }

    /* Spinner */
    .squadron-config__spinner {
      display: inline-block; width: 12px; height: 12px; border: 2px solid #93c5fd;
      border-top-color: #2563eb; border-radius: 50%; animation: sq-spin 0.8s linear infinite;
    }
    @keyframes sq-spin { to { transform: rotate(360deg); } }

    /* Log entries */
    .squadron-config__test-log { display: flex; flex-direction: column; gap: 4px; margin-bottom: 12px; }
    .squadron-config__test-log-entry {
      display: flex; gap: 8px; font-size: 0.75rem; font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', monospace;
      padding: 4px 8px; border-radius: 3px; background: #f9fafb;
    }
    .squadron-config__test-log-entry--success { background: #f0fdf4; color: #166534; }
    .squadron-config__test-log-entry--warning { background: #fffbeb; color: #92400e; }
    .squadron-config__test-log-entry--error { background: #fef2f2; color: #991b1b; }
    .squadron-config__test-log-phase {
      font-weight: 600; min-width: 100px; flex-shrink: 0; color: #6b7280;
    }
    .squadron-config__test-log-message { word-break: break-word; white-space: pre-wrap; }

    /* Agent output */
    .squadron-config__test-output { border: 1px solid #e5e7eb; border-radius: 4px; overflow: hidden; }
    .squadron-config__test-output-header {
      display: flex; justify-content: space-between; padding: 8px 12px;
      background: #f9fafb; cursor: pointer; font-size: 0.8125rem; font-weight: 500;
      user-select: none;
    }
    .squadron-config__test-output-header:hover { background: #f3f4f6; }
    .squadron-config__test-output-content {
      padding: 12px; font-size: 0.75rem; font-family: 'SF Mono', 'Fira Code', monospace;
      background: #1e293b; color: #e2e8f0; overflow-x: auto; max-height: 400px; overflow-y: auto;
      margin: 0; white-space: pre-wrap; word-break: break-word;
    }

    /* Interactive chat panel */
    .squadron-config__chat-panel {
      margin-top: 12px; border: 1px solid #c7d2fe; border-radius: 8px; overflow: hidden;
      display: flex; flex-direction: column; max-height: 500px;
    }
    .squadron-config__chat-header {
      display: flex; justify-content: space-between; align-items: center;
      padding: 10px 14px; background: #eef2ff; border-bottom: 1px solid #c7d2fe;
    }
    .squadron-config__chat-title { display: flex; align-items: center; gap: 8px; }
    .squadron-config__chat-agent-name { font-weight: 600; font-size: 0.875rem; }
    .squadron-config__chat-model { font-size: 0.75rem; color: #6b7280; }
    .squadron-config__chat-streaming-badge {
      font-size: 0.6875rem; font-weight: 500; color: #4f46e5; font-style: italic;
    }
    .squadron-config__chat-container-badge {
      font-size: 0.625rem; font-family: monospace; color: #059669; background: #ecfdf5;
      padding: 1px 6px; border-radius: 3px; border: 1px solid #a7f3d0;
    }
    .squadron-config__btn--close-chat {
      background: #fee2e2; color: #dc2626; border-color: #fecaca; font-size: 0.75rem;
    }
    .squadron-config__btn--close-chat:hover { background: #fecaca; }
    .squadron-config__btn--interactive {
      background: #eef2ff; color: #4f46e5; border-color: #c7d2fe;
    }
    .squadron-config__btn--interactive:hover { background: #e0e7ff; }
    .squadron-config__chat-body {
      flex: 1; overflow-y: auto; padding: 12px; display: flex; flex-direction: column; gap: 10px;
      min-height: 200px; max-height: 350px; background: #fafbfc;
    }
    .squadron-config__chat-message {
      padding: 8px 12px; border-radius: 8px; max-width: 85%;
    }
    .squadron-config__chat-message--user {
      background: #eef2ff; align-self: flex-end; border-bottom-right-radius: 2px;
    }
    .squadron-config__chat-message--agent {
      background: #fff; border: 1px solid #e5e7eb; align-self: flex-start; border-bottom-left-radius: 2px;
    }
    .squadron-config__chat-message--system {
      background: #f3f4f6; align-self: center; text-align: center;
      font-size: 0.75rem; color: #6b7280; max-width: 95%;
    }
    .squadron-config__chat-message-role {
      font-size: 0.6875rem; font-weight: 600; color: #6b7280; margin-bottom: 2px;
      text-transform: uppercase; letter-spacing: 0.03em;
    }
    .squadron-config__chat-message--system .squadron-config__chat-message-role { display: none; }
    .squadron-config__chat-message-content {
      font-size: 0.8125rem; line-height: 1.5; word-break: break-word;
    }
    .squadron-config__chat-message-content pre {
      background: #1e293b; color: #e2e8f0; padding: 8px 10px; border-radius: 4px;
      margin: 6px 0; overflow-x: auto; font-size: 0.75rem; line-height: 1.4;
      font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', monospace;
    }
    .squadron-config__chat-message-content .inline-code {
      background: rgba(79, 70, 229, 0.1); color: #4338ca; padding: 1px 4px;
      border-radius: 3px; font-family: 'SF Mono', 'Fira Code', monospace; font-size: 0.8rem;
    }
    .squadron-config__chat-message-content .squadron-config__chat-latex {
      background: #f8f5ff; color: #4338ca; padding: 10px 12px; border-radius: 4px;
      margin: 6px 0; overflow-x: auto; font-size: 0.8rem; line-height: 1.5;
      font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', monospace;
      border-left: 3px solid #a78bfa; white-space: pre-wrap;
    }
    .squadron-config__chat-message-content .squadron-config__chat-latex-inline {
      background: rgba(167, 139, 250, 0.1); color: #5b21b6; padding: 1px 4px;
      border-radius: 3px; font-family: 'SF Mono', 'Fira Code', monospace; font-size: 0.8rem;
    }
    .squadron-config__chat-message-content .squadron-config__chat-h1 {
      font-size: 1.25rem; font-weight: 700; margin: 12px 0 6px; line-height: 1.3;
    }
    .squadron-config__chat-message-content .squadron-config__chat-h2 {
      font-size: 1.1rem; font-weight: 700; margin: 10px 0 4px; line-height: 1.3;
    }
    .squadron-config__chat-message-content .squadron-config__chat-h3 {
      font-size: 1rem; font-weight: 600; margin: 8px 0 4px; line-height: 1.3;
    }
    .squadron-config__chat-message-content .squadron-config__chat-h4 {
      font-size: 0.9rem; font-weight: 600; margin: 6px 0 3px; line-height: 1.3; color: #374151;
    }
    .squadron-config__chat-message-content .squadron-config__chat-list-item {
      padding-left: 12px; margin: 2px 0;
    }
    .squadron-config__chat-message-content .squadron-config__chat-hr {
      border: none; border-top: 1px solid #d1d5db; margin: 8px 0;
    }
    .squadron-config__chat-message-content a {
      color: #4f46e5; text-decoration: underline;
    }
    .squadron-config__chat-message-content a:hover { color: #4338ca; }
    .squadron-config__chat-message-tokens {
      display: block; margin-top: 4px; font-size: 0.6875rem; color: #9ca3af;
      font-family: 'SF Mono', 'Fira Code', monospace;
    }
    .squadron-config__chat-input {
      display: flex; gap: 8px; padding: 10px 12px; border-top: 1px solid #e5e7eb;
      background: #fff;
    }
    .squadron-config__chat-input-field { flex: 1; }
    .squadron-config__btn--send {
      background: #4f46e5; color: #fff; border-color: #4f46e5; font-size: 0.8125rem;
    }
    .squadron-config__btn--send:hover { background: #4338ca; }
    .squadron-config__btn--send:disabled { background: #a5b4fc; border-color: #a5b4fc; }

    /* Typing indicator */
    .squadron-config__chat-typing {
      display: flex; gap: 4px; padding: 4px 0;
    }
    .squadron-config__chat-typing span {
      width: 7px; height: 7px; background: #9ca3af; border-radius: 50%;
      animation: sq-chat-pulse 1.4s ease infinite;
    }
    .squadron-config__chat-typing span:nth-child(2) { animation-delay: 0.2s; }
    .squadron-config__chat-typing span:nth-child(3) { animation-delay: 0.4s; }
    @keyframes sq-chat-pulse {
      0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
      40% { opacity: 1; transform: scale(1); }
    }
  `],
})
export class SquadronConfigComponent implements OnInit, OnDestroy {
  private squadronService = inject(UserSquadronService);
  private testService = inject(AgentTestService);

  loading = signal(true);
  saving = signal(false);
  saveSuccess = signal(false);
  saveError = signal<string | null>(null);
  agents = signal<UserAgentConfig[]>([]);
  maxAgents = signal(8);
  editingId = signal<string | null>(null);

  // Test state
  testMenuAgentId = signal<string | null>(null);
  testingAgentIds = signal<Set<string>>(new Set());
  testResults = signal<Map<string, AgentTestResult>>(new Map());
  expandedTests = signal<Set<string>>(new Set());
  expandedOutputs = signal<Set<string>>(new Set());

  // Interactive test state
  interactiveSessions = signal<Map<string, InteractiveTestSession>>(new Map());
  chatInputs: Map<string, string> = new Map();
  private interactiveSubs = new Map<string, Subscription>();

  // Edit form fields
  editName = '';
  editProvider = '';
  editModel = '';
  editHostingType: HostingType = 'PLATFORM';
  editBaseUrl = '';
  editApiKeyRef = '';
  editDescription = '';
  editSystemPrompt = '';
  editMaxTokens: number | null = null;
  editTemperature: number | null = null;
  editEnabled = true;

  /** Providers filtered by the selected hosting type. */
  filteredProviders = signal<ProviderCatalogEntry[]>([]);

  /** Models available for the currently selected provider. */
  availableModels = signal<ModelCatalogEntry[]>([]);

  /** The currently selected provider catalog entry (if any). */
  selectedProviderEntry = signal<ProviderCatalogEntry | null>(null);

  ngOnInit(): void {
    this.loadSquadron();
    this.loadLimits();
  }

  loadSquadron(): void {
    this.loading.set(true);
    this.squadronService.getMySquadron().subscribe({
      next: (agents) => {
        this.agents.set(agents);
        this.loading.set(false);
      },
      error: () => {
        this.agents.set([]);
        this.loading.set(false);
      },
    });
  }

  loadLimits(): void {
    this.squadronService.getLimits().subscribe({
      next: (limits) => this.maxAgents.set(limits.maxAgentsPerUser),
      error: () => { /* keep default */ },
    });
  }

  startEdit(agent: UserAgentConfig): void {
    this.editingId.set(agent.id ?? null);
    this.editName = agent.agentName;
    this.editProvider = agent.provider ?? '';
    this.editModel = agent.model ?? '';
    this.editHostingType = agent.hostingType ?? 'PLATFORM';
    this.editBaseUrl = agent.baseUrl ?? '';
    this.editApiKeyRef = agent.apiKeyRef ?? '';
    this.editDescription = agent.description ?? '';
    this.editSystemPrompt = agent.systemPromptOverride ?? '';
    this.editMaxTokens = agent.maxTokens ?? null;
    this.editTemperature = agent.temperature ?? null;
    this.editEnabled = agent.enabled;
    this.updateFilteredProviders();
    this.updateAvailableModels();
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  onHostingTypeChange(): void {
    this.editProvider = '';
    this.editModel = '';
    this.editBaseUrl = '';
    this.editApiKeyRef = '';
    this.updateFilteredProviders();
    this.updateAvailableModels();
  }

  onProviderChange(): void {
    this.editModel = '';
    this.updateAvailableModels();
  }

  /** Returns a auto-generated description based on current edit form state. */
  autoDescription(): string {
    return generateAgentDescription(this.editProvider, this.editModel, this.editHostingType);
  }

  /** Translates a hosting type to a display label. */
  getHostingLabel(hostingType: string): string {
    switch (hostingType) {
      case 'PLATFORM': return 'Cloud';
      case 'SELF_HOSTED': return 'Local';
      case 'CUSTOM': return 'Custom';
      default: return hostingType;
    }
  }

  // ====================== Test Methods ======================

  ngOnDestroy(): void {
    // Close all interactive sessions and unsubscribe
    this.interactiveSubs.forEach((sub) => sub.unsubscribe());
    this.interactiveSubs.clear();
  }

  openTestMenu(agent: UserAgentConfig): void {
    if (this.testMenuAgentId() === agent.id) {
      this.testMenuAgentId.set(null);
    } else {
      this.testMenuAgentId.set(agent.id ?? null);
    }
  }

  closeTestMenu(): void {
    this.testMenuAgentId.set(null);
  }

  runTest(agent: UserAgentConfig, mode: TestMode): void {
    if (!agent.id) return;
    this.testMenuAgentId.set(null);
    this.testingAgentIds.update(ids => { const s = new Set(ids); s.add(agent.id!); return s; });

    // Set initial running state
    const runningResult: AgentTestResult = {
      testId: '',
      agentConfigId: agent.id,
      testMode: mode,
      status: 'RUNNING',
      summary: 'Running ' + TEST_MODE_LABELS[mode] + '...',
      logEntries: [
        { timestamp: new Date().toISOString(), phase: 'INIT', message: 'Starting test...', level: 'INFO' },
      ],
    };
    this.updateTestResult(agent.id, runningResult);
    this.expandTest(agent.id);

    this.testService.executeTest({ agentConfigId: agent.id, testMode: mode }).subscribe({
      next: (result) => {
        // Each SSE event is a snapshot — update progressively
        this.updateTestResult(agent.id!, result);
        // If the result has a final status, test is done
        if (result.status === 'SUCCESS' || result.status === 'FAILURE' || result.status === 'ERROR') {
          this.testingAgentIds.update(ids => { const s = new Set(ids); s.delete(agent.id!); return s; });
        }
      },
      error: (err) => {
        const errorResult: AgentTestResult = {
          testId: '',
          agentConfigId: agent.id!,
          testMode: mode,
          status: 'ERROR',
          summary: 'Test failed: ' + (err.message || 'Unknown error'),
          logEntries: [
            { timestamp: new Date().toISOString(), phase: 'ERROR', message: 'Request failed: ' + (err.message || 'network error'), level: 'ERROR' },
          ],
        };
        this.updateTestResult(agent.id!, errorResult);
        this.testingAgentIds.update(ids => { const s = new Set(ids); s.delete(agent.id!); return s; });
      },
      complete: () => {
        // Ensure testing state is cleared on stream completion
        this.testingAgentIds.update(ids => { const s = new Set(ids); s.delete(agent.id!); return s; });
      },
    });
  }

  testResultForAgent(agentId: string): AgentTestResult | null {
    return this.testResults().get(agentId) ?? null;
  }

  isTestExpanded(agentId: string): boolean {
    return this.expandedTests().has(agentId);
  }

  toggleTestExpanded(agentId: string): void {
    const expanded = new Set(this.expandedTests());
    if (expanded.has(agentId)) {
      expanded.delete(agentId);
    } else {
      expanded.add(agentId);
    }
    this.expandedTests.set(expanded);
  }

  isOutputExpanded(agentId: string): boolean {
    return this.expandedOutputs().has(agentId);
  }

  toggleOutputExpanded(agentId: string): void {
    const expanded = new Set(this.expandedOutputs());
    if (expanded.has(agentId)) {
      expanded.delete(agentId);
    } else {
      expanded.add(agentId);
    }
    this.expandedOutputs.set(expanded);
  }

  dismissTestResult(agentId: string): void {
    const results = new Map(this.testResults());
    results.delete(agentId);
    this.testResults.set(results);
    const expanded = new Set(this.expandedTests());
    expanded.delete(agentId);
    this.expandedTests.set(expanded);
  }

  private updateTestResult(agentId: string, result: AgentTestResult): void {
    const results = new Map(this.testResults());
    results.set(agentId, result);
    this.testResults.set(results);
  }

  private expandTest(agentId: string): void {
    const expanded = new Set(this.expandedTests());
    expanded.add(agentId);
    this.expandedTests.set(expanded);
  }

  // ====================== Interactive Test Methods ======================

  interactiveSessionForAgent(agentId: string): InteractiveTestSession | null {
    return this.interactiveSessions().get(agentId) ?? null;
  }

  hasStreamingAgentMessage(session: InteractiveTestSession): boolean {
    // The backend adds a temporary streaming AGENT message in the snapshot when content is being streamed
    const msgs = session.messages;
    if (msgs.length === 0) return false;
    const last = msgs[msgs.length - 1];
    return last.role === 'AGENT' && session.status === 'STREAMING';
  }

  getChatInput(agentId: string): string {
    return this.chatInputs.get(agentId) ?? '';
  }

  setChatInput(agentId: string, value: string): void {
    this.chatInputs.set(agentId, value);
  }

  startInteractiveTest(agent: UserAgentConfig): void {
    if (!agent.id) return;
    this.testMenuAgentId.set(null);

    // If session already exists for this agent, don't start a new one
    if (this.interactiveSessions().has(agent.id)) return;

    // Cancel any existing startup subscription for this agent
    this.interactiveSubs.get(agent.id)?.unsubscribe();

    const sub = this.testService.startInteractiveSession(agent.id).subscribe({
      next: (snapshot) => {
        const sessions = new Map(this.interactiveSessions());
        sessions.set(agent.id!, snapshot);
        this.interactiveSessions.set(sessions);
        this.scrollChatToBottom(agent.id!);
      },
      error: () => {
        this.saveError.set('Failed to start interactive session.');
        setTimeout(() => this.saveError.set(null), 5000);
      },
      complete: () => {
        this.interactiveSubs.delete(agent.id!);
      },
    });

    this.interactiveSubs.set(agent.id, sub);
  }

  sendInteractiveMessage(agentId: string): void {
    const message = this.getChatInput(agentId).trim();
    if (!message) return;

    const session = this.interactiveSessions().get(agentId);
    if (!session) return;

    // Clear input immediately
    this.chatInputs.set(agentId, '');

    // Cancel any existing streaming subscription for this agent
    this.interactiveSubs.get(agentId)?.unsubscribe();

    const sub = this.testService.sendInteractiveMessage(session.sessionId, message).subscribe({
      next: (snapshot) => {
        const sessions = new Map(this.interactiveSessions());
        sessions.set(agentId, snapshot);
        this.interactiveSessions.set(sessions);
        this.scrollChatToBottom(agentId);
      },
      error: (err) => {
        // Add error to the session's messages locally
        const sessions = new Map(this.interactiveSessions());
        const current = sessions.get(agentId);
        if (current) {
          const errorMsg: InteractiveTestMessage = {
            id: crypto.randomUUID(),
            role: 'SYSTEM',
            content: 'Error: ' + (err.message || 'Failed to send message'),
            createdAt: new Date().toISOString(),
          };
          sessions.set(agentId, {
            ...current,
            status: 'ACTIVE',
            messages: [...current.messages, errorMsg],
          });
          this.interactiveSessions.set(sessions);
        }
      },
      complete: () => {
        this.interactiveSubs.delete(agentId);
      },
    });

    this.interactiveSubs.set(agentId, sub);
  }

  closeInteractiveSession(agentId: string): void {
    const session = this.interactiveSessions().get(agentId);
    if (!session) return;

    // Unsubscribe from any streaming
    this.interactiveSubs.get(agentId)?.unsubscribe();
    this.interactiveSubs.delete(agentId);

    // Remove from local state immediately
    const sessions = new Map(this.interactiveSessions());
    sessions.delete(agentId);
    this.interactiveSessions.set(sessions);
    this.chatInputs.delete(agentId);

    // Close on the backend (fire-and-forget)
    this.testService.closeInteractiveSession(session.sessionId).subscribe();
  }

  formatCodeBlocks(content: string): string {
    // Fenced code blocks first (before any line-level processing)
    let result = content
      .replace(/```(\w+)?\n([\s\S]*?)```/g, '<pre><code class="lang-$1">$2</code></pre>');

    // Block LaTeX: $$...$$ (may span multiple lines)
    result = result.replace(/\$\$([\s\S]*?)\$\$/g,
      '<pre class="squadron-config__chat-latex">$1</pre>');

    // Inline LaTeX: $...$ (single line, not preceded/followed by space+$)
    result = result.replace(/(?<!\$)\$(?!\$)([^$\n]+?)(?<!\$)\$(?!\$)/g,
      '<code class="squadron-config__chat-latex-inline">$1</code>');

    // Inline code
    result = result.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>');

    // Bold and italic
    result = result.replace(/\*\*\*(.+?)\*\*\*/g, '<strong><em>$1</em></strong>');
    result = result.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    result = result.replace(/(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)/g, '<em>$1</em>');

    // Links: [text](url)
    result = result.replace(/\[([^\]]+)\]\(([^)]+)\)/g,
      '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');

    // Horizontal rules (---, ***, ___)
    result = result.replace(/^([-*_]{3,})$/gm, '<hr class="squadron-config__chat-hr">');

    // Headings (must be processed before newline→<br>)
    result = result.replace(/^#### (.+)$/gm, '<div class="squadron-config__chat-h4">$1</div>');
    result = result.replace(/^### (.+)$/gm, '<div class="squadron-config__chat-h3">$1</div>');
    result = result.replace(/^## (.+)$/gm, '<div class="squadron-config__chat-h2">$1</div>');
    result = result.replace(/^# (.+)$/gm, '<div class="squadron-config__chat-h1">$1</div>');

    // Unordered lists: lines starting with - or *
    result = result.replace(/^[*-] (.+)$/gm,
      '<div class="squadron-config__chat-list-item">&#8226; $1</div>');

    // Ordered lists: lines starting with 1. 2. etc.
    result = result.replace(/^\d+\. (.+)$/gm,
      '<div class="squadron-config__chat-list-item">$&</div>');

    // Newlines to <br> (but not inside <pre> blocks)
    result = result.replace(/\n/g, '<br>');

    return result;
  }

  private scrollChatToBottom(agentId: string): void {
    setTimeout(() => {
      const chatBody = document.querySelector(
        `.squadron-config__chat-body[data-agent-id="${agentId}"]`
      );
      if (chatBody) {
        chatBody.scrollTop = chatBody.scrollHeight;
      }
    }, 50);
  }

  // ====================== CRUD Methods ======================

  saveAgent(agent: UserAgentConfig): void {
    if (!agent.id) return;
    this.saving.set(true);
    this.saveSuccess.set(false);
    this.saveError.set(null);

    const description = this.editDescription || this.autoDescription();

    const update: Partial<UserAgentConfig> = {
      agentName: this.editName,
      agentType: agent.agentType,
      displayOrder: agent.displayOrder,
      provider: this.editProvider || undefined,
      model: this.editModel || undefined,
      hostingType: this.editHostingType,
      baseUrl: this.editBaseUrl || undefined,
      apiKeyRef: this.editApiKeyRef || undefined,
      description,
      systemPromptOverride: this.editSystemPrompt || undefined,
      maxTokens: this.editMaxTokens ?? undefined,
      temperature: this.editTemperature ?? undefined,
      enabled: this.editEnabled,
    };

    this.squadronService.updateAgent(agent.id, update).subscribe({
      next: (updated) => {
        this.agents.update((list) =>
          list.map((a) => (a.id === updated.id ? updated : a)),
        );
        this.editingId.set(null);
        this.saving.set(false);
        this.saveSuccess.set(true);
        setTimeout(() => this.saveSuccess.set(false), 3000);
      },
      error: () => {
        this.saving.set(false);
        this.saveError.set('Failed to save agent. Please try again.');
        setTimeout(() => this.saveError.set(null), 5000);
      },
    });
  }

  addAgent(): void {
    const order = this.agents().length;
    const newAgent: Partial<UserAgentConfig> = {
      agentName: `Agent ${order + 1}`,
      agentType: 'GENERAL',
      displayOrder: order,
      hostingType: 'PLATFORM',
      enabled: true,
    };

    this.squadronService.addAgent(newAgent).subscribe({
      next: (agent) => {
        this.agents.update((list) => [...list, agent]);
        this.startEdit(agent);
      },
      error: () => {
        this.saveError.set('Failed to add agent.');
        setTimeout(() => this.saveError.set(null), 5000);
      },
    });
  }

  removeAgent(agent: UserAgentConfig): void {
    if (!agent.id || this.agents().length <= 1) return;

    this.squadronService.removeAgent(agent.id).subscribe({
      next: () => {
        this.agents.update((list) => list.filter((a) => a.id !== agent.id));
        if (this.editingId() === agent.id) {
          this.editingId.set(null);
        }
      },
      error: () => {
        this.saveError.set('Failed to remove agent.');
        setTimeout(() => this.saveError.set(null), 5000);
      },
    });
  }

  resetToDefaults(): void {
    this.squadronService.resetToDefaults().subscribe({
      next: (agents) => {
        this.agents.set(agents);
        this.editingId.set(null);
        this.saveSuccess.set(true);
        setTimeout(() => this.saveSuccess.set(false), 3000);
      },
      error: () => {
        this.saveError.set('Failed to reset squadron.');
        setTimeout(() => this.saveError.set(null), 5000);
      },
    });
  }

  private updateFilteredProviders(): void {
    if (this.editHostingType === 'CUSTOM') {
      this.filteredProviders.set(PROVIDER_CATALOG);
    } else {
      this.filteredProviders.set(
        PROVIDER_CATALOG.filter(p => p.hostingType === this.editHostingType),
      );
    }
  }

  private updateAvailableModels(): void {
    const provider = PROVIDER_CATALOG.find(p => p.id === this.editProvider) ?? null;
    this.selectedProviderEntry.set(provider);
    this.availableModels.set(provider?.models ?? []);
  }
}
