import { Component, input, computed } from '@angular/core';

@Component({
  selector: 'sq-avatar',
  standalone: true,
  template: `
    @if (src()) {
      <img [src]="src()" [alt]="name()" class="avatar" [style.width.px]="size()" [style.height.px]="size()" />
    } @else {
      <div class="avatar avatar--initials" [style.width.px]="size()" [style.height.px]="size()" [style.font-size.px]="fontSize()">
        {{ initials() }}
      </div>
    }
  `,
  styleUrl: './avatar.component.scss',
})
export class AvatarComponent {
  readonly name = input<string>('');
  readonly src = input<string | undefined>(undefined);
  readonly size = input<number>(32);

  readonly fontSize = computed(() => this.size() * 0.4);
  readonly initials = computed(() => {
    const n = this.name();
    if (!n) return '?';
    const parts = n.split(' ').filter(Boolean);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
    }
    return n.substring(0, 2).toUpperCase();
  });
}
