import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'timeAgo',
  standalone: true,
})
export class TimeAgoPipe implements PipeTransform {
  transform(value: string | Date): string {
    if (!value) return '';

    const date = typeof value === 'string' ? new Date(value) : value;
    const now = new Date();
    const seconds = Math.floor((now.getTime() - date.getTime()) / 1000);

    if (seconds < 60) return 'just now';
    if (seconds < 3600) {
      const minutes = Math.floor(seconds / 60);
      return `${minutes}m ago`;
    }
    if (seconds < 86400) {
      const hours = Math.floor(seconds / 3600);
      return `${hours}h ago`;
    }
    if (seconds < 604800) {
      const days = Math.floor(seconds / 86400);
      return `${days}d ago`;
    }
    if (seconds < 2592000) {
      const weeks = Math.floor(seconds / 604800);
      return `${weeks}w ago`;
    }
    const months = Math.floor(seconds / 2592000);
    return `${months}mo ago`;
  }
}
