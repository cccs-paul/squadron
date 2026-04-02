import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { UserTokensComponent } from './user-tokens.component';
import { UserTokenService } from '../../../core/services/user-token.service';
import { AuthService } from '../../../core/auth/auth.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { UserPlatformToken, ConnectionInfo } from '../../../core/models/user-token.model';

describe('UserTokensComponent', () => {
  let component: UserTokensComponent;
  let fixture: ComponentFixture<UserTokensComponent>;
  let tokenServiceSpy: jasmine.SpyObj<UserTokenService>;

  const mockAuthService = {
    user: signal({
      id: 'user-1',
      username: 'testuser',
      email: 'test@example.com',
      displayName: 'Test User',
      tenantId: 'tenant-1',
      tenantName: 'Demo Tenant',
      roles: ['developer'],
      permissions: [],
    }),
    isAuthenticated: signal(true),
    isAdmin: signal(false),
    getAccessToken: jasmine.createSpy('getAccessToken').and.returnValue('mock-token'),
  };

  const mockTokens: UserPlatformToken[] = [
    {
      id: 'tok-1',
      userId: 'user-1',
      connectionId: 'conn-1',
      tokenType: 'PAT',
      hasRefreshToken: false,
      createdAt: '2025-06-15T10:00:00Z',
    },
    {
      id: 'tok-2',
      userId: 'user-1',
      connectionId: 'conn-2',
      tokenType: 'OAUTH2',
      hasRefreshToken: true,
      createdAt: '2025-07-01T12:00:00Z',
      expiresAt: '2026-07-01T12:00:00Z',
    },
  ];

  const mockConnections: ConnectionInfo[] = [
    {
      id: 'conn-1',
      tenantId: 'tenant-1',
      name: 'GitHub Cloud',
      platformType: 'GITHUB',
      baseUrl: 'https://api.github.com',
      authType: 'PAT',
      status: 'ACTIVE',
      createdAt: '2025-01-01T00:00:00Z',
    },
    {
      id: 'conn-2',
      tenantId: 'tenant-1',
      name: 'GitLab SaaS',
      platformType: 'GITLAB',
      baseUrl: 'https://gitlab.com',
      authType: 'OAUTH2',
      status: 'ACTIVE',
      createdAt: '2025-01-01T00:00:00Z',
    },
    {
      id: 'conn-3',
      tenantId: 'tenant-1',
      name: 'Jira Cloud',
      platformType: 'JIRA',
      baseUrl: 'https://myorg.atlassian.net',
      authType: 'PAT',
      status: 'ACTIVE',
      createdAt: '2025-02-01T00:00:00Z',
    },
  ];

  beforeEach(async () => {
    tokenServiceSpy = jasmine.createSpyObj('UserTokenService', [
      'getTokensByUser',
      'getAvailableConnections',
      'linkPatAccount',
      'unlinkAccount',
    ]);

    await TestBed.configureTestingModule({
      imports: [UserTokensComponent],
      providers: [
        { provide: UserTokenService, useValue: tokenServiceSpy },
        { provide: AuthService, useValue: mockAuthService },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(UserTokensComponent);
    component = fixture.componentInstance;
  });

  it('should_create', () => {
    tokenServiceSpy.getTokensByUser.and.returnValue(of([]));
    tokenServiceSpy.getAvailableConnections.and.returnValue(of([]));
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should_loadTokensOnInit_when_userHasTokens', () => {
    tokenServiceSpy.getTokensByUser.and.returnValue(of(mockTokens));
    tokenServiceSpy.getAvailableConnections.and.returnValue(of(mockConnections));
    fixture.detectChanges();

    expect(tokenServiceSpy.getTokensByUser).toHaveBeenCalledWith('user-1');
    expect(component.tokens()).toEqual(mockTokens);
    expect(component.tokens().length).toBe(2);
  });

  it('should_loadConnectionsOnInit_when_tenantHasConnections', () => {
    tokenServiceSpy.getTokensByUser.and.returnValue(of(mockTokens));
    tokenServiceSpy.getAvailableConnections.and.returnValue(of(mockConnections));
    fixture.detectChanges();

    expect(tokenServiceSpy.getAvailableConnections).toHaveBeenCalledWith('tenant-1');
    expect(component.connections()).toEqual(mockConnections);
    expect(component.connections().length).toBe(3);
    expect(component.loading()).toBeFalse();
  });

  it('should_showEmptyState_when_noTokensExist', () => {
    tokenServiceSpy.getTokensByUser.and.returnValue(of([]));
    tokenServiceSpy.getAvailableConnections.and.returnValue(of(mockConnections));
    fixture.detectChanges();

    expect(component.tokens().length).toBe(0);
    const compiled = fixture.nativeElement as HTMLElement;
    const emptyMsg = compiled.querySelector('.user-tokens__empty');
    expect(emptyMsg).toBeTruthy();
    expect(emptyMsg?.textContent).toContain('No platform tokens linked yet');
  });

  it('should_showTokenCards_when_tokensExist', () => {
    tokenServiceSpy.getTokensByUser.and.returnValue(of(mockTokens));
    tokenServiceSpy.getAvailableConnections.and.returnValue(of(mockConnections));
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const cards = compiled.querySelectorAll('.user-tokens__token-card');
    expect(cards.length).toBe(2);
  });

  it('should_getConnectionName_when_tokenHasMatchingConnection', () => {
    tokenServiceSpy.getTokensByUser.and.returnValue(of(mockTokens));
    tokenServiceSpy.getAvailableConnections.and.returnValue(of(mockConnections));
    fixture.detectChanges();

    expect(component.getConnectionName('conn-1')).toBe('GitHub Cloud');
    expect(component.getConnectionName('conn-2')).toBe('GitLab SaaS');
    expect(component.getConnectionName('conn-3')).toBe('Jira Cloud');
  });

  it('should_returnUnknownConnection_when_noMatchFound', () => {
    tokenServiceSpy.getTokensByUser.and.returnValue(of(mockTokens));
    tokenServiceSpy.getAvailableConnections.and.returnValue(of(mockConnections));
    fixture.detectChanges();

    expect(component.getConnectionName('conn-nonexistent')).toBe('Unknown connection');
  });

  it('should_getUnlinkedConnections_when_someAlreadyLinked', () => {
    tokenServiceSpy.getTokensByUser.and.returnValue(of(mockTokens));
    tokenServiceSpy.getAvailableConnections.and.returnValue(of(mockConnections));
    fixture.detectChanges();

    // mockTokens link conn-1 and conn-2, so only conn-3 should be unlinked
    const unlinked = component.getUnlinkedConnections();
    expect(unlinked.length).toBe(1);
    expect(unlinked[0].id).toBe('conn-3');
  });

  it('should_toggleLinkForm_when_buttonClicked', () => {
    tokenServiceSpy.getTokensByUser.and.returnValue(of([]));
    tokenServiceSpy.getAvailableConnections.and.returnValue(of(mockConnections));
    fixture.detectChanges();

    expect(component.showLinkForm()).toBeFalse();

    component.toggleLinkForm();
    expect(component.showLinkForm()).toBeTrue();
    expect(component.selectedConnectionId).toBe('');
    expect(component.patValue).toBe('');

    component.toggleLinkForm();
    expect(component.showLinkForm()).toBeFalse();
  });

  it('should_linkPat_when_formSubmitted', () => {
    tokenServiceSpy.getTokensByUser.and.returnValue(of([]));
    tokenServiceSpy.getAvailableConnections.and.returnValue(of(mockConnections));
    fixture.detectChanges();

    const newToken: UserPlatformToken = {
      id: 'tok-new',
      userId: 'user-1',
      connectionId: 'conn-3',
      tokenType: 'PAT',
      hasRefreshToken: false,
      createdAt: '2025-08-01T00:00:00Z',
    };
    tokenServiceSpy.linkPatAccount.and.returnValue(of(newToken));

    component.showLinkForm.set(true);
    component.selectedConnectionId = 'conn-3';
    component.patValue = 'ghp_abc123';
    component.linkPat();

    expect(tokenServiceSpy.linkPatAccount).toHaveBeenCalledWith({
      userId: 'user-1',
      connectionId: 'conn-3',
      accessToken: 'ghp_abc123',
    });
    expect(component.tokens().length).toBe(1);
    expect(component.tokens()[0].id).toBe('tok-new');
    expect(component.linking()).toBeFalse();
    expect(component.linkSuccess()).toBeTrue();
    expect(component.showLinkForm()).toBeFalse();
  });

  it('should_showLinkError_when_linkFails', () => {
    tokenServiceSpy.getTokensByUser.and.returnValue(of([]));
    tokenServiceSpy.getAvailableConnections.and.returnValue(of(mockConnections));
    fixture.detectChanges();

    tokenServiceSpy.linkPatAccount.and.returnValue(throwError(() => new Error('fail')));

    component.showLinkForm.set(true);
    component.selectedConnectionId = 'conn-1';
    component.patValue = 'bad-token';
    component.linkPat();

    expect(component.linking()).toBeFalse();
    expect(component.linkError()).toBe('Failed to link token. Please check your token and try again.');
  });

  it('should_showValidationError_when_formIncomplete', () => {
    tokenServiceSpy.getTokensByUser.and.returnValue(of([]));
    tokenServiceSpy.getAvailableConnections.and.returnValue(of(mockConnections));
    fixture.detectChanges();

    // No connection selected, no pat value
    component.selectedConnectionId = '';
    component.patValue = '';
    component.linkPat();

    expect(component.linkError()).toBe('Please select a connection and enter a token.');
    expect(tokenServiceSpy.linkPatAccount).not.toHaveBeenCalled();

    // Connection selected but empty pat
    component.linkError.set(null);
    component.selectedConnectionId = 'conn-1';
    component.patValue = '   ';
    component.linkPat();

    expect(component.linkError()).toBe('Please select a connection and enter a token.');
    expect(tokenServiceSpy.linkPatAccount).not.toHaveBeenCalled();
  });

  it('should_unlinkToken_when_unlinkClicked', () => {
    tokenServiceSpy.getTokensByUser.and.returnValue(of(mockTokens));
    tokenServiceSpy.getAvailableConnections.and.returnValue(of(mockConnections));
    fixture.detectChanges();

    tokenServiceSpy.unlinkAccount.and.returnValue(of(undefined as any));

    expect(component.tokens().length).toBe(2);
    component.unlinkToken(mockTokens[0]);

    expect(tokenServiceSpy.unlinkAccount).toHaveBeenCalledWith('user-1', 'conn-1');
    expect(component.tokens().length).toBe(1);
    expect(component.tokens()[0].id).toBe('tok-2');
  });

  it('should_fallbackToEmptyTokens_when_loadFails', () => {
    tokenServiceSpy.getTokensByUser.and.returnValue(throwError(() => new Error('fail')));
    tokenServiceSpy.getAvailableConnections.and.returnValue(of(mockConnections));
    fixture.detectChanges();

    expect(component.tokens()).toEqual([]);
    expect(component.connections()).toEqual(mockConnections);
    expect(component.loading()).toBeFalse();
  });

  it('should_fallbackToEmptyConnections_when_connectionLoadFails', () => {
    tokenServiceSpy.getTokensByUser.and.returnValue(of(mockTokens));
    tokenServiceSpy.getAvailableConnections.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();

    expect(component.tokens()).toEqual(mockTokens);
    expect(component.connections()).toEqual([]);
    expect(component.loading()).toBeFalse();
  });

  it('should_formatDate_when_validDateProvided', () => {
    tokenServiceSpy.getTokensByUser.and.returnValue(of([]));
    tokenServiceSpy.getAvailableConnections.and.returnValue(of([]));
    fixture.detectChanges();

    const formatted = component.formatDate('2025-06-15T10:00:00Z');
    expect(formatted).toContain('Jun');
    expect(formatted).toContain('15');
    expect(formatted).toContain('2025');
  });

  it('should_returnDash_when_emptyDateProvided', () => {
    tokenServiceSpy.getTokensByUser.and.returnValue(of([]));
    tokenServiceSpy.getAvailableConnections.and.returnValue(of([]));
    fixture.detectChanges();

    expect(component.formatDate('')).toBe('\u2014');
  });
});
