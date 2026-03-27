import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, input, output, NO_ERRORS_SCHEMA } from '@angular/core';
import { provideRouter } from '@angular/router';
import { MainLayoutComponent } from './main-layout.component';
import { SidebarComponent } from '../../shared/components/sidebar/sidebar.component';
import { HeaderComponent } from '../../shared/components/header/header.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

@Component({ selector: 'sq-header', standalone: true, template: '' })
class MockHeaderComponent {
  readonly menuToggle = output<void>();
}

@Component({ selector: 'sq-sidebar', standalone: true, template: '' })
class MockSidebarComponent {
  readonly collapsed = input<boolean>(false);
}

describe('MainLayoutComponent', () => {
  let component: MainLayoutComponent;
  let fixture: ComponentFixture<MainLayoutComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MainLayoutComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    })
      .overrideComponent(MainLayoutComponent, {
        remove: { imports: [SidebarComponent, HeaderComponent] },
        add: { imports: [MockSidebarComponent, MockHeaderComponent] },
      })
      .compileComponents();

    fixture = TestBed.createComponent(MainLayoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize sidebarCollapsed to false', () => {
    expect(component.sidebarCollapsed).toBeFalse();
  });

  it('should toggle sidebar to true on first call', () => {
    component.toggleSidebar();
    expect(component.sidebarCollapsed).toBeTrue();
  });

  it('should toggle sidebar back to false on second call', () => {
    component.toggleSidebar();
    component.toggleSidebar();
    expect(component.sidebarCollapsed).toBeFalse();
  });

  it('should render a router-outlet', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('router-outlet')).toBeTruthy();
  });

  it('should render main-layout wrapper', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.main-layout')).toBeTruthy();
  });

  it('should add collapsed class when sidebar is collapsed', () => {
    component.sidebarCollapsed = true;
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.main-layout--collapsed')).toBeTruthy();
  });

  it('should not have collapsed class initially', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.main-layout--collapsed')).toBeNull();
  });
});
