import { TimeAgoPipe } from './time-ago.pipe';

describe('TimeAgoPipe', () => {
  let pipe: TimeAgoPipe;

  beforeEach(() => {
    pipe = new TimeAgoPipe();
  });

  it('should_beCreated', () => {
    expect(pipe).toBeTruthy();
  });

  it('should_returnEmptyString_when_valueIsNull', () => {
    expect(pipe.transform(null as any)).toBe('');
  });

  it('should_returnEmptyString_when_valueIsEmptyString', () => {
    expect(pipe.transform('')).toBe('');
  });

  it('should_returnJustNow_when_lessThanOneMinuteAgo', () => {
    const now = new Date();
    expect(pipe.transform(now)).toBe('just now');
  });

  it('should_returnJustNow_when_30SecondsAgo', () => {
    const date = new Date(Date.now() - 30 * 1000);
    expect(pipe.transform(date)).toBe('just now');
  });

  it('should_returnMinutesAgo_when_fewMinutesAgo', () => {
    const date = new Date(Date.now() - 5 * 60 * 1000);
    expect(pipe.transform(date)).toBe('5m ago');
  });

  it('should_return1mAgo_when_exactlyOneMinuteAgo', () => {
    const date = new Date(Date.now() - 60 * 1000);
    expect(pipe.transform(date)).toBe('1m ago');
  });

  it('should_returnHoursAgo_when_fewHoursAgo', () => {
    const date = new Date(Date.now() - 3 * 60 * 60 * 1000);
    expect(pipe.transform(date)).toBe('3h ago');
  });

  it('should_return1hAgo_when_exactlyOneHourAgo', () => {
    const date = new Date(Date.now() - 60 * 60 * 1000);
    expect(pipe.transform(date)).toBe('1h ago');
  });

  it('should_returnDaysAgo_when_fewDaysAgo', () => {
    const date = new Date(Date.now() - 3 * 24 * 60 * 60 * 1000);
    expect(pipe.transform(date)).toBe('3d ago');
  });

  it('should_return1dAgo_when_exactlyOneDayAgo', () => {
    const date = new Date(Date.now() - 24 * 60 * 60 * 1000);
    expect(pipe.transform(date)).toBe('1d ago');
  });

  it('should_returnWeeksAgo_when_fewWeeksAgo', () => {
    const date = new Date(Date.now() - 2 * 7 * 24 * 60 * 60 * 1000);
    expect(pipe.transform(date)).toBe('2w ago');
  });

  it('should_return1wAgo_when_exactlyOneWeekAgo', () => {
    const date = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);
    expect(pipe.transform(date)).toBe('1w ago');
  });

  it('should_returnMonthsAgo_when_fewMonthsAgo', () => {
    const date = new Date(Date.now() - 3 * 30 * 24 * 60 * 60 * 1000);
    expect(pipe.transform(date)).toBe('3mo ago');
  });

  it('should_handleStringDates_when_isoStringProvided', () => {
    const fiveMinutesAgo = new Date(Date.now() - 5 * 60 * 1000).toISOString();
    expect(pipe.transform(fiveMinutesAgo)).toBe('5m ago');
  });

  it('should_return59mAgo_when_justUnderOneHour', () => {
    const date = new Date(Date.now() - 59 * 60 * 1000);
    expect(pipe.transform(date)).toBe('59m ago');
  });
});
