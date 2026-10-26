import { Temporal } from '@js-temporal/polyfill';

export function toSessionInstant(date: string, time: string, timezone: string): string {
  return Temporal.PlainDateTime.from(`${date}T${time}`)
    .toZonedDateTime(timezone, { disambiguation: 'reject' })
    .toInstant()
    .toString();
}

export function instantToLocalFields(instant: Date, timezone: string): { date: string; time: string } {
  const zoned = Temporal.Instant.from(instant.toISOString()).toZonedDateTimeISO(timezone);
  return {
    date: zoned.toPlainDate().toString(),
    time: `${String(zoned.hour).padStart(2, '0')}:${String(zoned.minute).padStart(2, '0')}`,
  };
}

export function nextWholeHour(timezone: string): { date: string; time: string } {
  const nextHour = Temporal.Now.zonedDateTimeISO(timezone)
    .add({ hours: 1 })
    .with({ minute: 0, second: 0, millisecond: 0, microsecond: 0, nanosecond: 0 });
  return {
    date: nextHour.toPlainDate().toString(),
    time: `${String(nextHour.hour).padStart(2, '0')}:00`,
  };
}