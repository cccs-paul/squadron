import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { DiffService } from '../../core/services/diff.service';
import { DiffFile, DiffResult } from '../../core/models/diff.model';
import { ReviewComment, ReviewSeverity, ReviewCategory } from '../../core/models/review.model';

export interface DiffLine {
  type: 'addition' | 'deletion' | 'context' | 'hunk-header';
  content: string;
  oldLineNum?: number;
  newLineNum?: number;
}

@Component({
  selector: 'sq-diff-viewer',
  standalone: true,
  imports: [FormsModule, TranslateModule],
  templateUrl: './diff-viewer.component.html',
  styleUrl: './diff-viewer.component.scss',
})
export class DiffViewerComponent implements OnInit {
  private diffService = inject(DiffService);

  taskId = input.required<string>();
  comments = input<ReviewComment[]>([]);
  reviewId = input<string | null>(null);

  diff = signal<DiffResult | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);
  expandedFiles = signal<Set<string>>(new Set());
  commentingOn = signal<{ filename: string; lineNumber: number } | null>(null);

  // Plain properties for ngModel binding
  newCommentBody = '';
  newCommentSeverity = 'MINOR';
  newCommentCategory = 'BEST_PRACTICE';

  fileCount = computed(() => this.diff()?.files.length ?? 0);
  totalAdditions = computed(() => this.diff()?.totalAdditions ?? 0);
  totalDeletions = computed(() => this.diff()?.totalDeletions ?? 0);

  // Expose enums for template iteration
  readonly severities = Object.values(ReviewSeverity);
  readonly categories = Object.values(ReviewCategory);

  ngOnInit(): void {
    this.loadDiff();
  }

  loadDiff(): void {
    this.loading.set(true);
    this.error.set(null);

    this.diffService.getTaskDiff(this.taskId()).subscribe({
      next: (result) => {
        this.diff.set(result);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.message || 'Failed to load diff');
        this.loading.set(false);
      },
    });
  }

  toggleFile(filename: string): void {
    this.expandedFiles.update((set) => {
      const next = new Set(set);
      if (next.has(filename)) {
        next.delete(filename);
      } else {
        next.add(filename);
      }
      return next;
    });
  }

  isExpanded(filename: string): boolean {
    return this.expandedFiles().has(filename);
  }

  parsePatch(patch: string): DiffLine[] {
    if (!patch) return [];
    const lines = patch.split('\n');
    const result: DiffLine[] = [];
    let oldLine = 0;
    let newLine = 0;

    for (const line of lines) {
      if (line.startsWith('@@')) {
        const match = line.match(/@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@/);
        if (match) {
          oldLine = parseInt(match[1], 10);
          newLine = parseInt(match[2], 10);
        }
        result.push({ type: 'hunk-header', content: line });
      } else if (line.startsWith('+')) {
        result.push({ type: 'addition', content: line.substring(1), newLineNum: newLine++ });
      } else if (line.startsWith('-')) {
        result.push({ type: 'deletion', content: line.substring(1), oldLineNum: oldLine++ });
      } else {
        result.push({
          type: 'context',
          content: line.startsWith(' ') ? line.substring(1) : line,
          oldLineNum: oldLine++,
          newLineNum: newLine++,
        });
      }
    }
    return result;
  }

  getCommentsForLine(filename: string, lineNumber: number): ReviewComment[] {
    return this.comments().filter(
      (c) => c.filePath === filename && c.lineNumber === lineNumber
    );
  }

  startComment(filename: string, lineNumber: number): void {
    this.commentingOn.set({ filename, lineNumber });
    this.newCommentBody = '';
    this.newCommentSeverity = 'MINOR';
    this.newCommentCategory = 'BEST_PRACTICE';
  }

  cancelComment(): void {
    this.commentingOn.set(null);
    this.newCommentBody = '';
    this.newCommentSeverity = 'MINOR';
    this.newCommentCategory = 'BEST_PRACTICE';
  }

  isCommenting(filename: string, lineNumber: number): boolean {
    const c = this.commentingOn();
    return c !== null && c.filename === filename && c.lineNumber === lineNumber;
  }

  getEffectiveLineNum(line: DiffLine): number {
    return (line.type === 'deletion' ? line.oldLineNum : line.newLineNum) ?? 0;
  }

  getStatusIcon(status: string): string {
    switch (status) {
      case 'added': return '+';
      case 'modified': return '~';
      case 'deleted': return '-';
      case 'renamed': return '→';
      default: return '?';
    }
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'added': return 'added';
      case 'modified': return 'modified';
      case 'deleted': return 'deleted';
      case 'renamed': return 'renamed';
      default: return 'unknown';
    }
  }

  severityClass(severity: string): string {
    switch (severity) {
      case 'CRITICAL': return 'error';
      case 'MAJOR': return 'warning';
      case 'MINOR': return 'primary';
      default: return 'neutral';
    }
  }
}
