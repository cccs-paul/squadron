import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { SquadronConfigComponent } from './squadron-config.component';
import { UserSquadronService } from '../../../core/services/user-squadron.service';
import { UserAgentConfig, SquadronLimits } from '../../../core/models/squadron-config.model';
import { of, throwError } from 'rxjs';

describe('SquadronConfigComponent', () => {
  let component: SquadronConfigComponent;
  let fixture: ComponentFixture<SquadronConfigComponent>;
  let serviceSpy: jasmine.SpyObj<UserSquadronService>;

  function mockAgent(overrides: Partial<UserAgentConfig> = {}): UserAgentConfig {
    return {
      id: 'agent-1',
      agentName: 'Coder',
      agentType: 'CODING',
      displayOrder: 0,
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

    serviceSpy.getMySquadron.and.returnValue(of([
      mockAgent({ id: 'a1', agentName: 'Planner', agentType: 'PLANNING', displayOrder: 0 }),
      mockAgent({ id: 'a2', agentName: 'Coder', agentType: 'CODING', displayOrder: 1 }),
    ]));
    serviceSpy.getLimits.and.returnValue(of({ maxAgentsPerUser: 8 } as SquadronLimits));

    TestBed.configureTestingModule({
      imports: [SquadronConfigComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: UserSquadronService, useValue: serviceSpy },
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
    expect(names[0].textContent.trim()).toBe('Planner');
    expect(names[1].textContent.trim()).toBe('Coder');
  });

  it('should_displayAgentTypeBadges_when_loaded', () => {
    const badges = fixture.nativeElement.querySelectorAll('.squadron-config__agent-type-badge');
    expect(badges[0].textContent.trim()).toBe('PLANNING');
    expect(badges[1].textContent.trim()).toBe('CODING');
  });

  it('should_startEditing_when_editClicked', () => {
    component.startEdit(component.agents()[0]);
    expect(component.editingId()).toBe('a1');
    expect(component.editName).toBe('Planner');
    expect(component.editType).toBe('PLANNING');
  });

  it('should_cancelEditing_when_cancelClicked', () => {
    component.startEdit(component.agents()[0]);
    expect(component.editingId()).toBe('a1');

    component.cancelEdit();
    expect(component.editingId()).toBeNull();
  });

  it('should_saveAgent_when_saveClicked', () => {
    const updated = mockAgent({ id: 'a1', agentName: 'Updated Planner', agentType: 'PLANNING' });
    serviceSpy.updateAgent.and.returnValue(of(updated));

    component.startEdit(component.agents()[0]);
    component.editName = 'Updated Planner';
    component.saveAgent(component.agents()[0]);

    expect(serviceSpy.updateAgent).toHaveBeenCalledWith('a1', jasmine.objectContaining({
      agentName: 'Updated Planner',
    }));
    expect(component.editingId()).toBeNull();
    expect(component.agents()[0].agentName).toBe('Updated Planner');
  });

  it('should_showError_when_saveFails', () => {
    serviceSpy.updateAgent.and.returnValue(throwError(() => new Error('fail')));

    component.startEdit(component.agents()[0]);
    component.saveAgent(component.agents()[0]);

    expect(component.saveError()).toBe('Failed to save agent. Please try again.');
  });

  it('should_addAgent_when_addClicked', () => {
    const newAgent = mockAgent({ id: 'new-1', agentName: 'Agent 3', agentType: 'CODING', displayOrder: 2 });
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
    // Reduce to 1 agent first
    component.agents.set([mockAgent({ id: 'a1' })]);
    fixture.detectChanges();

    component.removeAgent(component.agents()[0]);

    expect(serviceSpy.removeAgent).not.toHaveBeenCalled();
  });

  it('should_resetToDefaults_when_resetClicked', () => {
    const defaults = [
      mockAgent({ id: 'd1', agentName: 'Default Planner', agentType: 'PLANNING' }),
    ];
    serviceSpy.resetToDefaults.and.returnValue(of(defaults));

    component.resetToDefaults();

    expect(serviceSpy.resetToDefaults).toHaveBeenCalled();
    expect(component.agents().length).toBe(1);
    expect(component.agents()[0].agentName).toBe('Default Planner');
  });

  it('should_handleLoadError_when_getMySquadronFails', () => {
    serviceSpy.getMySquadron.and.returnValue(throwError(() => new Error('fail')));

    component.loadSquadron();

    expect(component.agents().length).toBe(0);
    expect(component.loading()).toBeFalse();
  });

  it('should_haveAgentTypes_when_created', () => {
    expect(component.agentTypes).toEqual(['PLANNING', 'CODING', 'REVIEW', 'QA', 'MERGE', 'COVERAGE']);
  });

  it('should_showTitle_when_loaded', () => {
    const title = fixture.nativeElement.querySelector('.squadron-config__title');
    expect(title.textContent.trim()).toBe('My Agent Squadron');
  });

  it('should_showAddButton_when_loaded', () => {
    const addBtn = fixture.nativeElement.querySelector('.squadron-config__btn--add');
    expect(addBtn).toBeTruthy();
    expect(addBtn.textContent.trim()).toBe('+ Add Agent');
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
    expect(resetBtn.textContent.trim()).toBe('Reset to Defaults');
  });
});
