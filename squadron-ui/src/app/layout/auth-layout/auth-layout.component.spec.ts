import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AuthLayoutComponent } from './auth-layout.component';
import { provideRouter } from '@angular/router';

describe('AuthLayoutComponent', () => {
  let component: AuthLayoutComponent;
  let fixture: ComponentFixture<AuthLayoutComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuthLayoutComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(AuthLayoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render a router-outlet', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('router-outlet')).toBeTruthy();
  });

  it('should render auth-layout wrapper', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.auth-layout')).toBeTruthy();
  });

  it('should render auth-layout__content container', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.auth-layout__content')).toBeTruthy();
  });

  it('should render background pattern element', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.auth-layout__bg')).toBeTruthy();
  });
});
