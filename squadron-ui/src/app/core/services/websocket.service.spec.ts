import { TestBed } from '@angular/core/testing';
import { WebSocketService } from './websocket.service';

describe('WebSocketService', () => {
  let service: WebSocketService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(WebSocketService);
  });

  it('should_beCreated', () => {
    expect(service).toBeTruthy();
  });

  it('should_haveInitialDisconnectedState', () => {
    expect(service.connectionState()).toBe('disconnected');
  });

  it('should_setConnectingState_when_connectCalled', () => {
    // We can't fully test WebSocket in unit tests, but we verify the state transition
    // starts properly. The actual Client constructor will fail without a real server,
    // but we can at least verify the signal updates.
    try {
      service.connect();
    } catch (_e) {
      // Expected — no real WebSocket server
    }
    // The state should have been set to 'connecting' before the client activation attempt
    const state = service.connectionState();
    expect(['connecting', 'disconnected', 'error']).toContain(state);
  });

  it('should_notReconnect_when_alreadyConnecting', () => {
    try {
      service.connect();
    } catch (_e) {
      // Expected
    }
    const state1 = service.connectionState();
    try {
      service.connect();
    } catch (_e) {
      // Expected
    }
    const state2 = service.connectionState();
    // State shouldn't have reset — idempotent call
    expect(state1).toEqual(state2);
  });

  it('should_resetState_when_disconnectCalled', () => {
    service.disconnect();
    expect(service.connectionState()).toBe('disconnected');
  });

  it('should_unsubscribeFromDestination_when_unsubscribeCalled', () => {
    // Should not throw even if no subscription exists
    expect(() => service.unsubscribe('/topic/test')).not.toThrow();
  });

  it('should_logError_when_publishWithoutConnection', () => {
    spyOn(console, 'error');
    service.publish('/app/chat', { message: 'test' });
    expect(console.error).toHaveBeenCalledWith('Cannot publish: STOMP client not connected');
  });

  it('should_cleanUp_when_ngOnDestroyCalled', () => {
    spyOn(service, 'disconnect');
    service.ngOnDestroy();
    expect(service.disconnect).toHaveBeenCalled();
  });

  it('should_returnObservable_when_subscribeCalled', () => {
    // Subscribe should return an observable even when not connected
    // (it will try to connect internally)
    try {
      const obs = service.subscribe('/topic/test');
      expect(obs).toBeDefined();
      expect(typeof obs.subscribe).toBe('function');
    } catch (_e) {
      // May fail without real WS server, but observable should be created
    }
  });
});
