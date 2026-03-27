import { TruncatePipe } from './truncate.pipe';

describe('TruncatePipe', () => {
  let pipe: TruncatePipe;

  beforeEach(() => {
    pipe = new TruncatePipe();
  });

  it('should_beCreated', () => {
    expect(pipe).toBeTruthy();
  });

  it('should_returnEmptyString_when_valueIsNull', () => {
    expect(pipe.transform(null as any)).toBe('');
  });

  it('should_returnEmptyString_when_valueIsUndefined', () => {
    expect(pipe.transform(undefined as any)).toBe('');
  });

  it('should_returnEmptyString_when_valueIsEmptyString', () => {
    expect(pipe.transform('')).toBe('');
  });

  it('should_returnOriginalString_when_shorterThanDefaultLimit', () => {
    const shortText = 'Hello World';
    expect(pipe.transform(shortText)).toBe('Hello World');
  });

  it('should_returnOriginalString_when_exactlyAtDefaultLimit', () => {
    const text = 'a'.repeat(100);
    expect(pipe.transform(text)).toBe(text);
  });

  it('should_truncateWithEllipsis_when_longerThanDefaultLimit', () => {
    const longText = 'a'.repeat(150);
    const result = pipe.transform(longText);

    expect(result.length).toBe(103); // 100 chars + '...'
    expect(result.endsWith('...')).toBeTrue();
    expect(result.startsWith('aaaa')).toBeTrue();
  });

  it('should_truncateAtCustomLimit_when_limitProvided', () => {
    const text = 'This is a longer sentence that should be truncated at a custom limit.';
    const result = pipe.transform(text, 20);

    expect(result).toBe('This is a longer sen...');
  });

  it('should_useCustomEllipsis_when_ellipsisProvided', () => {
    const text = 'This is a longer sentence that should be truncated';
    const result = pipe.transform(text, 10, ' [...]');

    expect(result).toBe('This is a [...]');
  });

  it('should_trimTrailingWhitespace_when_truncationCutsAtSpace', () => {
    const text = 'Hello World, this is a test string for truncation';
    const result = pipe.transform(text, 12);

    // 'Hello World,' has 12 chars; .trim() removes trailing space
    expect(result).toBe('Hello World,...');
  });

  it('should_returnOriginalString_when_shorterThanCustomLimit', () => {
    expect(pipe.transform('short', 50)).toBe('short');
  });
});
