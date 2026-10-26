import { instantToLocalFields, toSessionInstant } from './session-time';

describe('session timezone conversion', () => {
  it('converts a local wall time to its UTC instant', () => {
    expect(toSessionInstant('2026-10-26', '08:15', 'Europe/Madrid'))
      .toBe('2026-10-26T07:15:00Z');
  });

  it('converts an instant back to local date and time', () => {
    expect(instantToLocalFields(new Date('2026-10-26T07:15:00Z'), 'Europe/Madrid'))
      .toEqual({ date: '2026-10-26', time: '08:15' });
  });

  it('rejects nonexistent and ambiguous daylight-saving wall times', () => {
    expect(() => toSessionInstant('2026-03-29', '02:30', 'Europe/Madrid')).toThrow();
    expect(() => toSessionInstant('2026-10-25', '02:30', 'Europe/Madrid')).toThrow();
  });
});