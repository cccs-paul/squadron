import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { SquadronConfigComponent } from './squadron-config.component';
import { UserSquadronService } from '../../../core/services/user-squadron.service';
import { AgentTestService } from '../../../core/services/agent-test.service';
import {
  UserAgentConfig,
  SquadronLimits,
  PROVIDER_CATALOG,
  generateAgentDescription,
} from '../../../core/models/squadron-config.model';
import { of, throwError } from 'rxjs';

describe('SquadronConfigComponent', () => {
  let component: SquadronConfigComponent;
  let fixture: ComponentFixture<SquadronConfigComponent>;
  let serviceSpy: jasmine.SpyObj<UserSquadronService>;
  let testServiceSpy: jasmine.SpyObj<AgentTestService>;

  function mockAgent(overrides: Partial<UserAgentConfig> = {}): UserAgentConfig {
    return {
      id: 'agent-1',
      agentName: 'Titan',
      agentType: 'GENERAL',
      displayOrder: 0,
      provider: 'ollama',
      model: 'gemma4:e2b',
      hostingType: 'SELF_HOSTED',
      description: 'Gemma 4 (local)',
      enabled: true,
      ...overrides,
    };
  }

  beforeEach(() => {
    serviceSpy = jasmine.createSpyObj('UserSquadronService', [
      'getMySquadron',
      'getLimits',
      'addAgent',
      'updateAgent',
      'removeAgent',
      'resetToDefaults',
    ]);

    testServiceSpy = jasmine.createSpyObj('AgentTestService', [
      'executeTest', 'getTestConfig', 'updateTestConfig',
    ]);
    testServiceSpy.executeTest.and.returnValue(of({
      testId: '', agentConfigId: '', testMode: 'PLANNING',
      status: 'SUCCESS', summary: '', logEntries: [],
    } as any));
    testServiceSpy.getTestConfig.and.returnValue(throwError(() => new Error('mock')));
    testServiceSpy.updateTestConfig.and.returnValue(of({} as any));

    serviceSpy.getMySquadron.and.returnValue(of([
      mockAgent({
        id: 'a1', agentName: 'Sol', displayOrder: 0,
        provider: 'ollama', model: 'gemma4:e2b',
        hostingType: 'SELF_HOSTED', description: 'Gemma 4 (local)',
      }),
      mockAgent({
        id: 'a2', agentName: 'Titan', displayOrder: 1,
        provider: 'ollama', model: 'gemma4:e2b',
        hostingType: 'SELF_HOSTED', description: 'Gemma 4 (local)',
      }),
    ]));
    serviceSpy.getLimits.and.returnValue(of({ maxAgentsPerUser: 8 } as SquadronLimits));

    TestBed.configureTestingModule({
      imports: [SquadronConfigComponent, TranslateModule.forRoot()],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: UserSquadronService, useValue: serviceSpy },
        { provide: AgentTestService, useValue: testServiceSpy },
      ],
    });

    fixture = TestBed.createComponent(SquadronConfigComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should_beCreated', () => {
    expect(component).toBeTruthy();
  });

  it('should_loadSquadron_when_initialized', () => {
    expect(serviceSpy.getMySquadron).toHaveBeenCalled();
    expect(component.agents().length).toBe(2);
    expect(component.loading()).toBeFalse();
  });

  it('should_loadLimits_when_initialized', () => {
    expect(serviceSpy.getLimits).toHaveBeenCalled();
    expect(component.maxAgents()).toBe(8);
  });

  it('should_renderAgentCards_when_loaded', () => {
    const cards = fixture.nativeElement.querySelectorAll('.squadron-config__agent-card');
    expect(cards.length).toBe(2);
  });

  it('should_displayAgentNames_when_loaded', () => {
    const names = fixture.nativeElement.querySelectorAll('.squadron-config__agent-name');
    expect(names[0].textContent.trim()).toBe('Sol');
    expect(names[1].textContent.trim()).toBe('Titan');
  });

  it('should_displayAgentDescriptions_when_loaded', () => {
    const descriptions = fixture.nativeElement.querySelectorAll('.squadron-config__agent-description');
    expect(descriptions[0].textContent.trim()).toBe('Gemma 4 (local)');
    expect(descriptions[1].textContent.trim()).toBe('Gemma 4 (local)');
  });

  it('should_displayHostingBadges_when_loaded', () => {
    const badges = fixture.nativeElement.querySelectorAll('.squadron-config__hosting-badge');
    expect(badges.length).toBe(2);
    expect(badges[0].textContent.trim()).toBe('Local');
  });

  it('should_startEditing_when_editClicked', () => {
    component.startEdit(component.agents()[0]);
    expect(component.editingId()).toBe('a1');
    expect(component.editName).toBe('Sol');
    expect(component.editProvider).toBe('ollama');
    expect(component.editModel).toBe('gemma4:e2b');
    expect(component.editHostingType).toBe('SELF_HOSTED');
    expect(component.editDescription).toBe('Gemma 4 (local)');
  });

  it('should_populateFilteredProviders_when_editingPlatformAgent', () => {
    component.startEdit(component.agents()[0]);
    const selfHostedProviders = PROVIDER_CATALOG.filter(p => p.hostingType === 'SELF_HOSTED');
    expect(component.filteredProviders().length).toBe(selfHostedProviders.length);
  });

  it('should_populateAvailableModels_when_editingAgentWithProvider', () => {
    component.startEdit(component.agents()[0]);
    const ollamaModels = PROVIDER_CATALOG.find(p => p.id === 'ollama')?.models ?? [];
    expect(component.availableModels().length).toBe(ollamaModels.length);
  });

  it('should_cancelEditing_when_cancelClicked', () => {
    component.startEdit(component.agents()[0]);
    expect(component.editingId()).toBe('a1');

    component.cancelEdit();
    expect(component.editingId()).toBeNull();
  });

  it('should_clearProviderAndModel_when_hostingTypeChanges', () => {
    component.startEdit(component.agents()[0]);
    component.editProvider = 'ollama';
    component.editModel = 'gemma4:e2b';

    component.editHostingType = 'SELF_HOSTED';
    component.onHostingTypeChange();

    expect(component.editProvider).toBe('');
    expect(component.editModel).toBe('');
  });

  it('should_clearModel_when_providerChanges', () => {
    component.startEdit(component.agents()[0]);
    component.editModel = 'gemma4:e2b';

    component.onProviderChange();

    expect(component.editModel).toBe('');
  });

  it('should_saveAgent_when_saveClicked', () => {
    const updated = mockAgent({
      id: 'a1', agentName: 'Updated Sol',
      provider: 'anthropic', model: 'claude-opus-4',
      hostingType: 'PLATFORM', description: 'Claude Opus 4 via Anthropic',
    });
    serviceSpy.updateAgent.and.returnValue(of(updated));

    component.startEdit(component.agents()[0]);
    component.editName = 'Updated Sol';
    component.editProvider = 'anthropic';
    component.editModel = 'claude-opus-4';
    component.editDescription = 'Claude Opus 4 via Anthropic';
    component.saveAgent(component.agents()[0]);

    expect(serviceSpy.updateAgent).toHaveBeenCalledWith('a1', jasmine.objectContaining({
      agentName: 'Updated Sol',
      provider: 'anthropic',
      model: 'claude-opus-4',
      hostingType: 'PLATFORM',
      description: 'Claude Opus 4 via Anthropic',
    }));
    expect(component.editingId()).toBeNull();
    expect(component.agents()[0].agentName).toBe('Updated Sol');
    expect(component.agents()[0].description).toBe('Claude Opus 4 via Anthropic');
  });

  it('should_autoGenerateDescription_when_descriptionEmpty', () => {
    const updated = mockAgent({
      id: 'a1', agentName: 'Sol',
      provider: 'openai', model: 'gpt-4o',
      hostingType: 'PLATFORM', description: 'GPT-4o via OpenAI',
    });
    serviceSpy.updateAgent.and.returnValue(of(updated));

    component.startEdit(component.agents()[0]);
    component.editProvider = 'openai';
    component.editModel = 'gpt-4o';
    component.editDescription = ''; // empty => auto-generate
    component.saveAgent(component.agents()[0]);

    expect(serviceSpy.updateAgent).toHaveBeenCalledWith('a1', jasmine.objectContaining({
      description: 'GPT-4o via OpenAI',
    }));
  });

  it('should_showError_when_saveFails', () => {
    serviceSpy.updateAgent.and.returnValue(throwError(() => new Error('fail')));

    component.startEdit(component.agents()[0]);
    component.saveAgent(component.agents()[0]);

    expect(component.saveError()).toBe('Failed to save agent. Please try again.');
  });

  it('should_addAgent_when_addClicked', () => {
    const newAgent = mockAgent({ id: 'new-1', agentName: 'Agent 3', displayOrder: 2 });
    serviceSpy.addAgent.and.returnValue(of(newAgent));

    component.addAgent();

    expect(serviceSpy.addAgent).toHaveBeenCalled();
    expect(component.agents().length).toBe(3);
  });

  it('should_removeAgent_when_removeClicked', () => {
    serviceSpy.removeAgent.and.returnValue(of(undefined as any));

    component.removeAgent(component.agents()[0]);

    expect(serviceSpy.removeAgent).toHaveBeenCalledWith('a1');
    expect(component.agents().length).toBe(1);
    expect(component.agents()[0].id).toBe('a2');
  });

  it('should_notRemoveAgent_when_onlyOneRemaining', () => {
    component.agents.set([mockAgent({ id: 'a1' })]);
    fixture.detectChanges();

    component.removeAgent(component.agents()[0]);

    expect(serviceSpy.removeAgent).not.toHaveBeenCalled();
  });

  it('should_resetToDefaults_when_resetClicked', () => {
    const defaults = [
      mockAgent({
        id: 'd1', agentName: 'Sol',
        provider: 'ollama', model: 'gemma4:e2b',
        description: 'Gemma 4 (local)',
      }),
    ];
    serviceSpy.resetToDefaults.and.returnValue(of(defaults));

    component.resetToDefaults();

    expect(serviceSpy.resetToDefaults).toHaveBeenCalled();
    expect(component.agents().length).toBe(1);
    expect(component.agents()[0].agentName).toBe('Sol');
  });

  it('should_handleLoadError_when_getMySquadronFails', () => {
    serviceSpy.getMySquadron.and.returnValue(throwError(() => new Error('fail')));

    component.loadSquadron();

    expect(component.agents().length).toBe(0);
    expect(component.loading()).toBeFalse();
  });

  it('should_showTitle_when_loaded', () => {
    const title = fixture.nativeElement.querySelector('.squadron-config__title');
    expect(title.textContent.trim()).toBe('settings.squadronConfig.title');
  });

  it('should_showAddButton_when_loaded', () => {
    const addBtn = fixture.nativeElement.querySelector('.squadron-config__btn--add');
    expect(addBtn).toBeTruthy();
    expect(addBtn.textContent.trim()).toBe('settings.squadronConfig.addAgent');
  });

  it('should_disableAddButton_when_atMaxAgents', () => {
    component.maxAgents.set(2);
    fixture.detectChanges();

    const addBtn = fixture.nativeElement.querySelector('.squadron-config__btn--add');
    expect(addBtn.disabled).toBeTrue();
  });

  it('should_showResetButton_when_loaded', () => {
    const resetBtn = fixture.nativeElement.querySelector('.squadron-config__btn--reset');
    expect(resetBtn).toBeTruthy();
    expect(resetBtn.textContent.trim()).toBe('settings.squadronConfig.resetDefaults');
  });

  it('should_getHostingLabel_for_platform', () => {
    expect(component.getHostingLabel('PLATFORM')).toBe('Cloud');
  });

  it('should_getHostingLabel_for_selfHosted', () => {
    expect(component.getHostingLabel('SELF_HOSTED')).toBe('Local');
  });

  it('should_getHostingLabel_for_custom', () => {
    expect(component.getHostingLabel('CUSTOM')).toBe('Custom');
  });

  it('should_filterProviders_for_selfHosted', () => {
    component.startEdit(component.agents()[0]);
    component.editHostingType = 'SELF_HOSTED';
    component.onHostingTypeChange();

    const selfHostedProviders = PROVIDER_CATALOG.filter(p => p.hostingType === 'SELF_HOSTED');
    expect(component.filteredProviders().length).toBe(selfHostedProviders.length);
    expect(component.filteredProviders().every(p => p.hostingType === 'SELF_HOSTED')).toBeTrue();
  });

  it('should_showAllProviders_for_custom', () => {
    component.startEdit(component.agents()[0]);
    component.editHostingType = 'CUSTOM';
    component.onHostingTypeChange();

    expect(component.filteredProviders().length).toBe(PROVIDER_CATALOG.length);
  });

  it('should_displayLocalBadge_for_selfHostedAgent', () => {
    component.agents.set([mockAgent({
      id: 'a1', agentName: 'Nebula',
      provider: 'ollama', model: 'llama3.3',
      hostingType: 'SELF_HOSTED', description: 'Llama 3.3 (local)',
    })]);
    fixture.detectChanges();

    const badge = fixture.nativeElement.querySelector('.squadron-config__hosting-badge--self-hosted');
    expect(badge).toBeTruthy();
    expect(badge.textContent.trim()).toBe('Local');
  });

  it('should_setSelectedProviderEntry_when_editingAgent', () => {
    component.startEdit(component.agents()[0]);
    expect(component.selectedProviderEntry()).toBeTruthy();
    expect(component.selectedProviderEntry()!.id).toBe('ollama');
  });

  it('should_showApiKeyField_when_providerRequiresApiKey', () => {
    component.agents.set([mockAgent({
      id: 'a1', agentName: 'Claude',
      provider: 'anthropic', model: 'claude-sonnet-4',
      hostingType: 'PLATFORM', description: 'Claude Sonnet 4 via Anthropic',
    })]);
    fixture.detectChanges();

    component.startEdit(component.agents()[0]);
    fixture.detectChanges();

    expect(component.selectedProviderEntry()?.requiresApiKey).toBeTrue();
    const apiKeyInput = fixture.nativeElement.querySelector('input[type="password"]');
    expect(apiKeyInput).toBeTruthy();
  });

  it('should_notShowApiKeyField_when_providerDoesNotRequireApiKey', () => {
    component.agents.set([mockAgent({
      id: 'a1', agentName: 'Copilot',
      provider: 'github-copilot', model: 'gpt-4o',
      hostingType: 'PLATFORM', description: 'GPT-4o via GitHub Copilot',
    })]);
    fixture.detectChanges();

    component.startEdit(component.agents()[0]);
    fixture.detectChanges();

    expect(component.selectedProviderEntry()?.requiresApiKey).toBeFalsy();
    const apiKeyInput = fixture.nativeElement.querySelector('input[type="password"]');
    expect(apiKeyInput).toBeFalsy();
  });

  it('should_showBaseUrlField_when_providerHasDefaultBaseUrl', () => {
    component.agents.set([mockAgent({
      id: 'a1', agentName: 'OpenAI Agent',
      provider: 'openai', model: 'gpt-4o',
      hostingType: 'PLATFORM', description: 'GPT-4o via OpenAI',
    })]);
    fixture.detectChanges();

    component.startEdit(component.agents()[0]);
    fixture.detectChanges();

    expect(component.selectedProviderEntry()?.defaultBaseUrl).toBe('https://api.openai.com/v1');
  });

  it('should_clearSelectedProviderEntry_when_hostingTypeChanges', () => {
    component.startEdit(component.agents()[0]);
    expect(component.selectedProviderEntry()).toBeTruthy();

    component.onHostingTypeChange();
    expect(component.selectedProviderEntry()).toBeNull();
  });

  // --- Test functionality ---

  it('should_openTestMenu_when_testButtonClicked', () => {
    component.openTestMenu(component.agents()[0]);
    expect(component.testMenuAgentId()).toBe('a1');
  });

  it('should_closeTestMenu_when_alreadyOpen', () => {
    component.openTestMenu(component.agents()[0]);
    expect(component.testMenuAgentId()).toBe('a1');
    component.openTestMenu(component.agents()[0]);
    expect(component.testMenuAgentId()).toBeNull();
  });

  it('should_closeTestMenu_when_closeTestMenuCalled', () => {
    component.openTestMenu(component.agents()[0]);
    component.closeTestMenu();
    expect(component.testMenuAgentId()).toBeNull();
  });

  it('should_renderTestButton_forEachAgent', () => {
    const testBtns = fixture.nativeElement.querySelectorAll('.squadron-config__btn--test');
    expect(testBtns.length).toBe(2);
  });

  it('should_renderTestMenu_when_testMenuOpen', () => {
    component.openTestMenu(component.agents()[0]);
    fixture.detectChanges();
    const menu = fixture.nativeElement.querySelector('.squadron-config__test-menu');
    expect(menu).toBeTruthy();
    const modeButtons = menu.querySelectorAll('.squadron-config__btn--test-mode');
    expect(modeButtons.length).toBe(3);
  });

  it('should_runTest_when_testModeSelected', () => {
    const mockResult = {
      testId: 't1',
      agentConfigId: 'a1',
      testMode: 'PLANNING' as const,
      status: 'SUCCESS' as const,
      summary: 'Test passed',
      logEntries: [{ timestamp: '2026-01-01T00:00:00Z', phase: 'DONE', message: 'ok', level: 'SUCCESS' as const }],
      durationMs: 1234,
    };
    testServiceSpy.executeTest.and.returnValue(of(mockResult));

    component.runTest(component.agents()[0], 'PLANNING');
    expect(component.testingAgentId()).toBeNull(); // completed
    expect(component.testResultForAgent('a1')).toBeTruthy();
    expect(component.testResultForAgent('a1')!.status).toBe('SUCCESS');
  });

  it('should_showErrorResult_when_testFails', () => {
    testServiceSpy.executeTest.and.returnValue(throwError(() => ({ status: 500, message: 'Server error' })));

    component.runTest(component.agents()[0], 'CODE_GENERATION');
    expect(component.testResultForAgent('a1')!.status).toBe('ERROR');
  });

  it('should_toggleTestExpanded', () => {
    expect(component.isTestExpanded('a1')).toBeFalse();
    component.toggleTestExpanded('a1');
    expect(component.isTestExpanded('a1')).toBeTrue();
    component.toggleTestExpanded('a1');
    expect(component.isTestExpanded('a1')).toBeFalse();
  });

  it('should_toggleOutputExpanded', () => {
    expect(component.isOutputExpanded('a1')).toBeFalse();
    component.toggleOutputExpanded('a1');
    expect(component.isOutputExpanded('a1')).toBeTrue();
    component.toggleOutputExpanded('a1');
    expect(component.isOutputExpanded('a1')).toBeFalse();
  });

  it('should_dismissTestResult', () => {
    const mockResult = {
      testId: 't1', agentConfigId: 'a1', testMode: 'PLANNING' as const,
      status: 'SUCCESS' as const, summary: 'ok', logEntries: [],
    };
    testServiceSpy.executeTest.and.returnValue(of(mockResult));
    component.runTest(component.agents()[0], 'PLANNING');
    expect(component.testResultForAgent('a1')).toBeTruthy();

    component.dismissTestResult('a1');
    expect(component.testResultForAgent('a1')).toBeNull();
  });

  it('should_notRunTest_when_agentHasNoId', () => {
    const noIdAgent = { ...component.agents()[0], id: undefined };
    component.runTest(noIdAgent as any, 'PLANNING');
    expect(testServiceSpy.executeTest).not.toHaveBeenCalled();
  });
});

describe('generateAgentDescription', () => {
  it('should_generatePlatformDescription', () => {
    expect(generateAgentDescription('anthropic', 'claude-opus-4', 'PLATFORM'))
      .toBe('Claude Opus 4 via Anthropic');
  });

  it('should_generateLocalDescription', () => {
    expect(generateAgentDescription('ollama', 'llama3.3', 'SELF_HOSTED'))
      .toBe('Llama 3.3 (local)');
  });

  it('should_generateCustomDescription', () => {
    expect(generateAgentDescription('custom', 'gpt-4o', 'CUSTOM'))
      .toBe('gpt-4o via Custom endpoint');
  });

  it('should_returnUnconfigured_when_noInput', () => {
    expect(generateAgentDescription(undefined, undefined, undefined))
      .toBe('Unconfigured');
  });

  it('should_returnProvider_when_noModel', () => {
    expect(generateAgentDescription('anthropic', undefined, 'PLATFORM'))
      .toBe('anthropic');
  });

  it('should_useRawModel_when_unknownModel', () => {
    expect(generateAgentDescription('openai', 'my-custom-model', 'PLATFORM'))
      .toBe('my-custom-model via OpenAI');
  });
});
