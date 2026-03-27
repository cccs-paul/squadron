import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { AvatarComponent } from './avatar.component';

// Test host component to set signal inputs
@Component({
  standalone: true,
  imports: [AvatarComponent],
  template: `<sq-avatar [name]="name" [src]="src" [size]="size" />`,
})
class TestHostComponent {
  name = '';
  src: string | undefined = undefined;
  size = 32;
}

describe('AvatarComponent', () => {
  let hostFixture: ComponentFixture<TestHostComponent>;
  let host: TestHostComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent],
    }).compileComponents();

    hostFixture = TestBed.createComponent(TestHostComponent);
    host = hostFixture.componentInstance;
    hostFixture.detectChanges();
  });

  it('should_beCreated', () => {
    const avatarEl = hostFixture.nativeElement.querySelector('sq-avatar');
    expect(avatarEl).toBeTruthy();
  });

  it('should_showImage_when_srcProvided', () => {
    host.src = 'https://example.com/avatar.png';
    host.name = 'John Doe';
    hostFixture.detectChanges();

    const img: HTMLImageElement = hostFixture.nativeElement.querySelector('img.avatar');
    expect(img).toBeTruthy();
    expect(img.src).toBe('https://example.com/avatar.png');
    expect(img.alt).toBe('John Doe');
  });

  it('should_showInitials_when_noSrcProvided', () => {
    host.name = 'John Doe';
    host.src = undefined;
    hostFixture.detectChanges();

    const initialsDiv = hostFixture.nativeElement.querySelector('.avatar--initials');
    expect(initialsDiv).toBeTruthy();
    expect(initialsDiv.textContent.trim()).toBe('JD');
  });

  it('should_showQuestionMark_when_noNameAndNoSrc', () => {
    host.name = '';
    host.src = undefined;
    hostFixture.detectChanges();

    const initialsDiv = hostFixture.nativeElement.querySelector('.avatar--initials');
    expect(initialsDiv).toBeTruthy();
    expect(initialsDiv.textContent.trim()).toBe('?');
  });

  it('should_computeInitials_when_singleName', () => {
    host.name = 'Alice';
    host.src = undefined;
    hostFixture.detectChanges();

    const initialsDiv = hostFixture.nativeElement.querySelector('.avatar--initials');
    expect(initialsDiv.textContent.trim()).toBe('AL');
  });

  it('should_computeInitials_when_threePartName', () => {
    host.name = 'John Michael Doe';
    host.src = undefined;
    hostFixture.detectChanges();

    const initialsDiv = hostFixture.nativeElement.querySelector('.avatar--initials');
    expect(initialsDiv.textContent.trim()).toBe('JD');
  });

  it('should_applySize_when_sizeProvided', () => {
    host.name = 'John Doe';
    host.src = undefined;
    host.size = 64;
    hostFixture.detectChanges();

    const initialsDiv: HTMLElement = hostFixture.nativeElement.querySelector('.avatar--initials');
    expect(initialsDiv.style.width).toBe('64px');
    expect(initialsDiv.style.height).toBe('64px');
  });

  it('should_applyFontSize_when_sizeProvided', () => {
    host.name = 'John Doe';
    host.src = undefined;
    host.size = 100;
    hostFixture.detectChanges();

    const initialsDiv: HTMLElement = hostFixture.nativeElement.querySelector('.avatar--initials');
    expect(initialsDiv.style.fontSize).toBe('40px');
  });

  it('should_applySizeToImage_when_srcAndSizeProvided', () => {
    host.src = 'https://example.com/avatar.png';
    host.name = 'John Doe';
    host.size = 48;
    hostFixture.detectChanges();

    const img: HTMLImageElement = hostFixture.nativeElement.querySelector('img.avatar');
    expect(img.style.width).toBe('48px');
    expect(img.style.height).toBe('48px');
  });

  it('should_notShowImage_when_srcIsUndefined', () => {
    host.name = 'John Doe';
    host.src = undefined;
    hostFixture.detectChanges();

    const img = hostFixture.nativeElement.querySelector('img.avatar');
    expect(img).toBeNull();
  });

  it('should_uppercaseInitials_when_lowercaseName', () => {
    host.name = 'jane smith';
    host.src = undefined;
    hostFixture.detectChanges();

    const initialsDiv = hostFixture.nativeElement.querySelector('.avatar--initials');
    expect(initialsDiv.textContent.trim()).toBe('JS');
  });
});
