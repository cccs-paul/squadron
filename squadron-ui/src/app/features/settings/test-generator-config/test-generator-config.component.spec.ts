import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { TestGeneratorConfigComponent } from './test-generator-config.component';
import { AgentTestService } from '../../../core/services/agent-test.service';
import { AgentTestConfig } from '../../../core/models/agent-test.model';
import { PROVIDER_CATALOG } from '../../../core/models/squadron-config.model';
import { of, throwError } from 'rxjs';

describe('TestGeneratorConfigComponent', () => {
  let component: TestGeneratorConfigComponent;
  let fixture: ComponentFixture<TestGeneratorConfigComponent>;
  let testServiceSpy: jasmine.SpyObj<AgentTestService>;

  const mockConfig: AgentTestConfig = {
    generatorProvider: 'anthropic',
    generatorModel: 'claude-sonnet-4',
    generatorHostingType: 'PLATFORM',
    generatorBaseUrl: '',
  };

  beforeEach(() => {
    testServiceSpy = jasmine.createSpyObj('AgentTestService', [
      'getTestConfig', 'updateTestConfig', 'executeTest',
    ]);
    testServiceSpy.getTestConfig.and.returnValue(of(mockConfig));
    testServiceSpy.updateTestConfig.and.returnValue(of(mockConfig));
    testServiceSpy.executeTest.and.returnValue(of({} as any));

    TestBed.configureTestingModule({
      imports: [TestGeneratorConfigComponent, TranslateModule.forRoot()],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AgentTestService, useValue: testServiceSpy },
      ],
    });

    fixture = TestBed.createComponent(TestGeneratorConfigComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should_beCreated', () => {
    expect(component).toBeTruthy();
  });

  it('should_loadConfig_when_initialized', () => {
    expect(testServiceSpy.getTestConfig).toHaveBeenCalled();
    expect(component.loading()).toBeFalse();
    expect(component.provider).toBe('anthropic');
    expect(component.model).toBe('claude-sonnet-4');
    expect(component.hostingType).toBe('PLATFORM');
  });

  it('should_useDefaults_when_loadConfigFails', () => {
    testServiceSpy.getTestConfig.and.returnValue(throwError(() => new Error('fail')));

    component.loadConfig();

    expect(component.loading()).toBeFalse();
    expect(component.hostingType).toBe('PLATFORM');
  });

  it('should_updateFilteredProviders_when_hostingTypeChanges', () => {
    component.hostingType = 'SELF_HOSTED';
    component.onHostingTypeChange();

    const selfHosted = PROVIDER_CATALOG.filter(p => p.hostingType === 'SELF_HOSTED');
    expect(component.filteredProviders().length).toBe(selfHosted.length);
  });

  it('should_updateAvailableModels_when_providerChanges', () => {
    component.hostingType = 'PLATFORM';
    component.onHostingTypeChange();
    component.provider = 'anthropic';
    component.onProviderChange();

    const models = PROVIDER_CATALOG.find(p => p.id === 'anthropic')?.models ?? [];
    expect(component.availableModels().length).toBe(models.length);
  });

  it('should_clearFields_when_hostingTypeChanges', () => {
    component.provider = 'anthropic';
    component.model = 'claude-sonnet-4';
    component.baseUrl = 'http://localhost';
    component.apiKey = 'secret';

    component.onHostingTypeChange();

    expect(component.provider).toBe('');
    expect(component.model).toBe('');
    expect(component.baseUrl).toBe('');
    expect(component.apiKey).toBe('');
  });

  it('should_clearModel_when_providerChanges', () => {
    component.model = 'some-model';
    component.onProviderChange();
    expect(component.model).toBe('');
  });

  it('should_saveConfig_when_saveClicked', () => {
    testServiceSpy.updateTestConfig.and.returnValue(of(mockConfig));

    component.save();

    expect(testServiceSpy.updateTestConfig).toHaveBeenCalled();
    expect(component.saving()).toBeFalse();
    expect(component.saveSuccess()).toBeTrue();
  });

  it('should_showSaveSuccess_when_saveSucceeds', fakeAsync(() => {
    testServiceSpy.updateTestConfig.and.returnValue(of(mockConfig));

    component.save();
    expect(component.saveSuccess()).toBeTrue();

    tick(3000);
    expect(component.saveSuccess()).toBeFalse();
  }));

  it('should_showSaveError_when_saveFails', () => {
    testServiceSpy.updateTestConfig.and.returnValue(throwError(() => new Error('fail')));

    component.save();

    expect(component.saving()).toBeFalse();
    expect(component.saveError()).toBe('Failed to save configuration.');
  });

  it('should_renderFormFields', () => {
    const fields = fixture.nativeElement.querySelectorAll('.test-gen-config__field');
    expect(fields.length).toBeGreaterThanOrEqual(3);
  });

  it('should_renderHostingTypeSelect', () => {
    const selects = fixture.nativeElement.querySelectorAll('.test-gen-config__select');
    expect(selects.length).toBeGreaterThanOrEqual(1);
  });

  it('should_renderProviderSelect', () => {
    const selects = fixture.nativeElement.querySelectorAll('.test-gen-config__select');
    expect(selects.length).toBeGreaterThanOrEqual(2);
  });

  it('should_renderSaveButton', () => {
    const btn = fixture.nativeElement.querySelector('.test-gen-config__btn--save');
    expect(btn).toBeTruthy();
  });

  it('should_disableSaveButton_when_saving', () => {
    component.saving.set(true);
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector('.test-gen-config__btn--save');
    expect(btn.disabled).toBeTrue();
  });

  it('should_showAllProviders_when_hostingTypeIsCustom', () => {
    component.hostingType = 'CUSTOM';
    component.onHostingTypeChange();

    expect(component.filteredProviders().length).toBe(PROVIDER_CATALOG.length);
  });
});
