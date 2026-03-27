import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DiffViewerComponent, DiffLine } from './diff-viewer.component';
import { DiffService } from '../../core/services/diff.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { DiffFile, DiffResult } from '../../core/models/diff.model';
import { ReviewComment, ReviewSeverity, ReviewCategory, ReviewerType } from '../../core/models/review.model';
import { Component } from '@angular/core';

const mockDiffResult: DiffResult = {
  files: [
    { filename: 'src/app.ts', status: 'modified', additions: 10, deletions: 3, patch: '@@ -1,5 +1,12 @@\n-old line\n+new line\n context' },
    { filename: 'src/utils.ts', status: 'added', additions: 25, deletions: 0, patch: '@@ -0,0 +1,25 @@\n+export function helper() {}' },
    { filename: 'src/old.ts', status: 'deleted', additions: 0, deletions: 15, patch: '@@ -1,15 +0,0 @@\n-removed content' },
  ],
  totalAdditions: 35,
  totalDeletions: 18,
};

const emptyDiffResult: DiffResult = {
  files: [],
  totalAdditions: 0,
  totalDeletions: 0,
};

const mockComments: ReviewComment[] = [
  {
    id: 'comment-1',
    filePath: 'src/app.ts',
    lineNumber: 1,
    body: 'This line has a bug',
    severity: ReviewSeverity.CRITICAL,
    category: ReviewCategory.BUG,
    resolved: false,
    authorName: 'AI Reviewer',
    authorType: ReviewerType.AI,
    createdAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 'comment-2',
    filePath: 'src/app.ts',
    lineNumber: 1,
    body: 'Style issue here',
    severity: ReviewSeverity.MINOR,
    category: ReviewCategory.STYLE,
    resolved: true,
    authorName: 'Human Dev',
    authorType: ReviewerType.HUMAN,
    createdAt: '2026-01-02T00:00:00Z',
  },
  {
    id: 'comment-3',
    filePath: 'src/utils.ts',
    lineNumber: 1,
    body: 'Consider performance',
    severity: ReviewSeverity.MAJOR,
    category: ReviewCategory.PERFORMANCE,
    resolved: false,
    authorName: 'AI Reviewer',
    authorType: ReviewerType.AI,
    createdAt: '2026-01-03T00:00:00Z',
  },
];

// Test host component to provide required inputs
@Component({
  standalone: true,
  imports: [DiffViewerComponent],
  template: `<sq-diff-viewer [taskId]="taskId" [comments]="comments" [reviewId]="reviewId" />`,
})
class TestHostComponent {
  taskId = 'task-123';
  comments: ReviewComment[] = [];
  reviewId: string | null = null;
}

describe('DiffViewerComponent', () => {
  let hostComponent: TestHostComponent;
  let component: DiffViewerComponent;
  let fixture: ComponentFixture<TestHostComponent>;
  let diffServiceSpy: jasmine.SpyObj<DiffService>;

  beforeEach(async () => {
    diffServiceSpy = jasmine.createSpyObj('DiffService', ['getTaskDiff', 'getCodeGenerationStatus', 'getPullRequestDiff']);
    diffServiceSpy.getTaskDiff.and.returnValue(of(mockDiffResult));

    await TestBed.configureTestingModule({
      imports: [TestHostComponent, DiffViewerComponent],
      providers: [
        { provide: DiffService, useValue: diffServiceSpy },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    hostComponent = fixture.componentInstance;
    fixture.detectChanges();
    component = fixture.debugElement.children[0].componentInstance;
  });

  it('should_create_component', () => {
    expect(component).toBeTruthy();
  });

  it('should_show_loading_initially', () => {
    diffServiceSpy.getTaskDiff.and.returnValue(of(mockDiffResult));
    const freshFixture = TestBed.createComponent(TestHostComponent);
    const freshComponent: DiffViewerComponent = freshFixture.debugElement.children[0].componentInstance;

    // Before detectChanges, loading should be true (default)
    expect(freshComponent.loading()).toBeTrue();

    freshFixture.detectChanges();
    // After load completes, loading should be false
    expect(freshComponent.loading()).toBeFalse();
  });

  it('should_display_diff_stats', () => {
    expect(component.totalAdditions()).toBe(35);
    expect(component.totalDeletions()).toBe(18);
    expect(component.fileCount()).toBe(3);

    const compiled = fixture.nativeElement as HTMLElement;
    const additionsEl = compiled.querySelector('.diff-viewer__stat--additions');
    expect(additionsEl?.textContent?.trim()).toBe('+35');

    const deletionsEl = compiled.querySelector('.diff-viewer__stat--deletions');
    expect(deletionsEl?.textContent?.trim()).toBe('-18');
  });

  it('should_display_file_list', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const fileHeaders = compiled.querySelectorAll('.diff-file__header');
    expect(fileHeaders.length).toBe(3);

    const fileNames = compiled.querySelectorAll('.diff-file__name');
    expect(fileNames[0].textContent?.trim()).toBe('src/app.ts');
    expect(fileNames[1].textContent?.trim()).toBe('src/utils.ts');
    expect(fileNames[2].textContent?.trim()).toBe('src/old.ts');
  });

  it('should_toggle_file_expansion', () => {
    expect(component.isExpanded('src/app.ts')).toBeFalse();

    component.toggleFile('src/app.ts');
    expect(component.isExpanded('src/app.ts')).toBeTrue();

    component.toggleFile('src/app.ts');
    expect(component.isExpanded('src/app.ts')).toBeFalse();
  });

  it('should_show_diff_table_when_expanded', () => {
    component.toggleFile('src/app.ts');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const diffTable = compiled.querySelector('.diff-table');
    expect(diffTable).toBeTruthy();

    const diffLines = compiled.querySelectorAll('.diff-line');
    expect(diffLines.length).toBeGreaterThan(0);
  });

  it('should_show_status_icons', () => {
    expect(component.getStatusIcon('added')).toBe('+');
    expect(component.getStatusIcon('modified')).toBe('~');
    expect(component.getStatusIcon('deleted')).toBe('-');
    expect(component.getStatusIcon('renamed')).toBe('→');
    expect(component.getStatusIcon('unknown')).toBe('?');
  });

  it('should_show_error_message', async () => {
    diffServiceSpy.getTaskDiff.and.returnValue(throwError(() => new Error('Network error')));

    const errorFixture = TestBed.createComponent(TestHostComponent);
    errorFixture.detectChanges();

    const errorComponent: DiffViewerComponent = errorFixture.debugElement.children[0].componentInstance;
    expect(errorComponent.error()).toBe('Network error');
    expect(errorComponent.loading()).toBeFalse();

    const compiled = errorFixture.nativeElement as HTMLElement;
    const errorEl = compiled.querySelector('.diff-viewer__error');
    expect(errorEl?.textContent?.trim()).toBe('Network error');
  });

  it('should_show_empty_state', async () => {
    diffServiceSpy.getTaskDiff.and.returnValue(of(emptyDiffResult));

    const emptyFixture = TestBed.createComponent(TestHostComponent);
    emptyFixture.detectChanges();

    const emptyComponent: DiffViewerComponent = emptyFixture.debugElement.children[0].componentInstance;
    expect(emptyComponent.fileCount()).toBe(0);

    const compiled = emptyFixture.nativeElement as HTMLElement;
    const emptyEl = compiled.querySelector('.diff-viewer__empty');
    expect(emptyEl?.textContent?.trim()).toBe('No file changes found.');
  });

  it('should_compute_file_count', () => {
    expect(component.fileCount()).toBe(3);

    // Test with null diff
    component.diff.set(null);
    expect(component.fileCount()).toBe(0);

    // Test with different data
    component.diff.set({
      files: [{ filename: 'test.ts', status: 'added', additions: 1, deletions: 0, patch: '' }],
      totalAdditions: 1,
      totalDeletions: 0,
    });
    expect(component.fileCount()).toBe(1);
  });

  it('should_return_correct_status_classes', () => {
    expect(component.getStatusClass('added')).toBe('added');
    expect(component.getStatusClass('modified')).toBe('modified');
    expect(component.getStatusClass('deleted')).toBe('deleted');
    expect(component.getStatusClass('renamed')).toBe('renamed');
    expect(component.getStatusClass('something')).toBe('unknown');
  });

  it('should_call_diffService_with_correct_taskId', () => {
    expect(diffServiceSpy.getTaskDiff).toHaveBeenCalledWith('task-123');
  });

  it('should_expand_multiple_files_independently', () => {
    component.toggleFile('src/app.ts');
    component.toggleFile('src/utils.ts');

    expect(component.isExpanded('src/app.ts')).toBeTrue();
    expect(component.isExpanded('src/utils.ts')).toBeTrue();
    expect(component.isExpanded('src/old.ts')).toBeFalse();

    component.toggleFile('src/app.ts');
    expect(component.isExpanded('src/app.ts')).toBeFalse();
    expect(component.isExpanded('src/utils.ts')).toBeTrue();
  });

  // --- New tests for diff line parsing and inline comments ---

  it('should_parse_patch_into_diff_lines', () => {
    const patch = '@@ -1,5 +1,12 @@\n-old line\n+new line\n context';
    const lines = component.parsePatch(patch);

    expect(lines.length).toBe(4);

    // Hunk header
    expect(lines[0].type).toBe('hunk-header');
    expect(lines[0].content).toBe('@@ -1,5 +1,12 @@');

    // Deletion
    expect(lines[1].type).toBe('deletion');
    expect(lines[1].content).toBe('old line');
    expect(lines[1].oldLineNum).toBe(1);
    expect(lines[1].newLineNum).toBeUndefined();

    // Addition
    expect(lines[2].type).toBe('addition');
    expect(lines[2].content).toBe('new line');
    expect(lines[2].newLineNum).toBe(1);
    expect(lines[2].oldLineNum).toBeUndefined();

    // Context
    expect(lines[3].type).toBe('context');
    expect(lines[3].content).toBe('context');
    expect(lines[3].oldLineNum).toBe(2);
    expect(lines[3].newLineNum).toBe(2);
  });

  it('should_parse_hunk_headers', () => {
    const patch = '@@ -10,6 +20,8 @@\n context line';
    const lines = component.parsePatch(patch);

    expect(lines[0].type).toBe('hunk-header');
    expect(lines[0].content).toBe('@@ -10,6 +20,8 @@');

    // Context line should start at the parsed line numbers
    expect(lines[1].type).toBe('context');
    expect(lines[1].oldLineNum).toBe(10);
    expect(lines[1].newLineNum).toBe(20);
  });

  it('should_handle_empty_patch', () => {
    expect(component.parsePatch('')).toEqual([]);
  });

  it('should_get_comments_for_line', () => {
    // Set up component with mock comments by creating a new fixture
    diffServiceSpy.getTaskDiff.and.returnValue(of(mockDiffResult));
    hostComponent.comments = mockComments;
    fixture.detectChanges();

    const commentsForApp1 = component.getCommentsForLine('src/app.ts', 1);
    expect(commentsForApp1.length).toBe(2);
    expect(commentsForApp1[0].id).toBe('comment-1');
    expect(commentsForApp1[1].id).toBe('comment-2');

    const commentsForUtils1 = component.getCommentsForLine('src/utils.ts', 1);
    expect(commentsForUtils1.length).toBe(1);
    expect(commentsForUtils1[0].id).toBe('comment-3');

    const commentsForNonExistent = component.getCommentsForLine('src/app.ts', 999);
    expect(commentsForNonExistent.length).toBe(0);
  });

  it('should_start_and_cancel_comment', () => {
    expect(component.commentingOn()).toBeNull();

    component.startComment('src/app.ts', 5);
    expect(component.commentingOn()).toEqual({ filename: 'src/app.ts', lineNumber: 5 });
    expect(component.newCommentBody).toBe('');
    expect(component.newCommentSeverity).toBe('MINOR');
    expect(component.newCommentCategory).toBe('BEST_PRACTICE');
    expect(component.isCommenting('src/app.ts', 5)).toBeTrue();
    expect(component.isCommenting('src/app.ts', 6)).toBeFalse();

    component.cancelComment();
    expect(component.commentingOn()).toBeNull();
    expect(component.isCommenting('src/app.ts', 5)).toBeFalse();
  });

  it('should_show_inline_comments_for_matching_lines', () => {
    hostComponent.comments = mockComments;
    hostComponent.reviewId = 'review-1';
    fixture.detectChanges();

    // Expand the file that has comments
    component.toggleFile('src/app.ts');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const inlineComments = compiled.querySelectorAll('.inline-comment');
    expect(inlineComments.length).toBeGreaterThan(0);

    // Check that comment body is rendered
    const commentBodies = compiled.querySelectorAll('.inline-comment__body');
    const bodyTexts = Array.from(commentBodies).map((el) => el.textContent?.trim());
    expect(bodyTexts).toContain('This line has a bug');
  });

  it('should_return_correct_severity_class', () => {
    expect(component.severityClass('CRITICAL')).toBe('error');
    expect(component.severityClass('MAJOR')).toBe('warning');
    expect(component.severityClass('MINOR')).toBe('primary');
    expect(component.severityClass('INFO')).toBe('neutral');
  });

  it('should_render_line_numbers_in_diff_table', () => {
    component.toggleFile('src/app.ts');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const lineNums = compiled.querySelectorAll('.line-num');
    expect(lineNums.length).toBeGreaterThan(0);

    // Check for old and new line number columns
    const oldNums = compiled.querySelectorAll('.line-num--old');
    const newNums = compiled.querySelectorAll('.line-num--new');
    expect(oldNums.length).toBeGreaterThan(0);
    expect(newNums.length).toBeGreaterThan(0);
  });

  it('should_render_clickable_line_numbers_when_reviewId_set', () => {
    hostComponent.reviewId = 'review-1';
    fixture.detectChanges();

    component.toggleFile('src/app.ts');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const clickableNums = compiled.querySelectorAll('.line-num__clickable');
    expect(clickableNums.length).toBeGreaterThan(0);
  });

  it('should_not_render_clickable_line_numbers_when_no_reviewId', () => {
    hostComponent.reviewId = null;
    fixture.detectChanges();

    component.toggleFile('src/app.ts');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const clickableNums = compiled.querySelectorAll('.line-num__clickable');
    expect(clickableNums.length).toBe(0);
  });

  it('should_parse_multiple_hunks', () => {
    const patch = '@@ -1,3 +1,3 @@\n-old\n+new\n ctx\n@@ -10,3 +10,3 @@\n-old2\n+new2\n ctx2';
    const lines = component.parsePatch(patch);

    // Should have 2 hunk headers
    const hunkHeaders = lines.filter((l) => l.type === 'hunk-header');
    expect(hunkHeaders.length).toBe(2);

    // Second hunk should start at line 10
    const secondHunkIdx = lines.indexOf(hunkHeaders[1]);
    const lineAfterSecondHunk = lines[secondHunkIdx + 1];
    expect(lineAfterSecondHunk.oldLineNum).toBe(10);
  });

  it('should_get_effective_line_num', () => {
    const additionLine: DiffLine = { type: 'addition', content: 'new', newLineNum: 5 };
    const deletionLine: DiffLine = { type: 'deletion', content: 'old', oldLineNum: 3 };
    const contextLine: DiffLine = { type: 'context', content: 'ctx', oldLineNum: 7, newLineNum: 8 };

    expect(component.getEffectiveLineNum(additionLine)).toBe(5);
    expect(component.getEffectiveLineNum(deletionLine)).toBe(3);
    expect(component.getEffectiveLineNum(contextLine)).toBe(8);
  });
});
