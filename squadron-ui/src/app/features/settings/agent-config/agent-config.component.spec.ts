import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AgentConfigComponent } from './agent-config.component';
import { AgentConfigService, AgentConfig } from '../../../core/services/agent-config.service';
import { AuthService } from '../../../core/auth/auth.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';

describe('AgentConfigComponent', () => {
  let component: AgentConfigComponent;
  let fixture: ComponentFixture<AgentConfigComponent>;
  let configServiceSpy: jasmine.SpyObj<AgentConfigService>;
  let authServiceMock: Partial<AuthService>;

  const mockConfig: AgentConfig = {
    id: 'cfg1',
    tenantId: 'demo-tenant-001',
    provider: 'OpenAI',
    modelName: 'gpt-4',
    temperature: 0.7,
    maxTokens: 4096,
    systemPrompt: 'You are a helpful coding assistant.',
    agentOverrides: {
      CODING: { tenantId: 'demo-tenant-001', provider: 'OpenAI', modelName: 'gpt-4-turbo', temperature: 0.3, maxTokens: 8192 },
    },
  };

  beforeEach(async () => {
    configServiceSpy = jasmine.createSpyObj('AgentConfigService', [
      'getConfig',
      'updateConfig',
    ]);

    authServiceMock = {
      user: signal({
        id: 'u1',
        username: 'testuser',
        email: 'test@example.com',
        displayName: 'Test User',
        tenantId: 'demo-tenant-001',
        tenantName: 'Demo Tenant',
        roles: ['squadron-admin'],
        permissions: [],
      }),
      isAuthenticated: signal(true),
      isAdmin: signal(true),
    } as any;

    await TestBed.configureTestingModule({
      imports: [AgentConfigComponent],
      providers: [
        { provide: AgentConfigService, useValue: configServiceSpy },
        { provide: AuthService, useValue: authServiceMock },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AgentConfigComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    configServiceSpy.getConfig.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should load config on init', () => {
    configServiceSpy.getConfig.and.returnValue(of(mockConfig));
    fixture.detectChanges();

    expect(component.provider).toBe('OpenAI');
    expect(component.modelName).toBe('gpt-4');
    expect(component.temperature).toBe(0.7);
    expect(component.maxTokens).toBe(4096);
    expect(component.systemPrompt).toBe('You are a helpful coding assistant.');
    expect(component.loading()).toBeFalse();
  });

  it('should update temperature', () => {
    configServiceSpy.getConfig.and.returnValue(of(mockConfig));
    fixture.detectChanges();

    component.temperature = 0.3;
    expect(component.temperature).toBe(0.3);
  });

  it('should select provider', () => {
    configServiceSpy.getConfig.and.returnValue(of(mockConfig));
    fixture.detectChanges();

    component.provider = 'Ollama';
    expect(component.provider).toBe('Ollama');
  });

  it('should save config', () => {
    configServiceSpy.getConfig.and.returnValue(of(mockConfig));
    fixture.detectChanges();

    configServiceSpy.updateConfig.and.returnValue(of(mockConfig));
    component.saveConfig();

    expect(configServiceSpy.updateConfig).toHaveBeenCalledWith(
      'demo-tenant-001',
      jasmine.objectContaining({ provider: 'OpenAI', modelName: 'gpt-4' }),
    );
    expect(component.saving()).toBeFalse();
    expect(component.saveSuccess()).toBeTrue();
  });

  it('should show agent type overrides', () => {
    configServiceSpy.getConfig.and.returnValue(of(mockConfig));
    fixture.detectChanges();

    expect(component.getOverrideTypes()).toEqual(['CODING']);
    expect(component.agentOverrides['CODING'].modelName).toBe('gpt-4-turbo');
  });

  it('should validate max tokens', () => {
    configServiceSpy.getConfig.and.returnValue(of(mockConfig));
    fixture.detectChanges();

    expect(component.validateMaxTokens()).toBeTrue();

    component.maxTokens = 0;
    expect(component.validateMaxTokens()).toBeFalse();

    component.maxTokens = 200000;
    expect(component.validateMaxTokens()).toBeFalse();

    component.maxTokens = 128000;
    expect(component.validateMaxTokens()).toBeTrue();
  });

  it('should handle error on save', () => {
    configServiceSpy.getConfig.and.returnValue(of(mockConfig));
    fixture.detectChanges();

    configServiceSpy.updateConfig.and.returnValue(throwError(() => new Error('fail')));
    component.saveConfig();

    expect(component.saving()).toBeFalse();
    expect(component.saveError()).toBe('Failed to save configuration. Please try again.');
  });

  it('should not save when max tokens invalid', () => {
    configServiceSpy.getConfig.and.returnValue(of(mockConfig));
    fixture.detectChanges();

    component.maxTokens = 0;
    component.saveConfig();

    expect(configServiceSpy.updateConfig).not.toHaveBeenCalled();
    expect(component.saveError()).toBe('Max tokens must be between 1 and 128000.');
  });

  it('should add and remove overrides', () => {
    configServiceSpy.getConfig.and.returnValue(of(mockConfig));
    fixture.detectChanges();

    component.addOverride('PLANNING');
    expect(component.agentOverrides['PLANNING']).toBeTruthy();
    expect(component.getOverrideTypes()).toContain('PLANNING');

    component.removeOverride('PLANNING');
    expect(component.agentOverrides['PLANNING']).toBeUndefined();
  });

  it('should fall back to defaults on load error', () => {
    configServiceSpy.getConfig.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();

    expect(component.provider).toBe('OpenAI');
    expect(component.modelName).toBe('gpt-4');
    expect(component.temperature).toBe(0.7);
    expect(component.maxTokens).toBe(4096);
    expect(component.loading()).toBeFalse();
  });
});
