/**
 * Week date utilities.
 *
 * All week boundaries are Monday-based ISO weeks. Dates are plain
 * ISO date strings (YYYY-MM-DD). No timezone conversion — the
 * server and client agree that weekStartDate is a calendar date.
 */

/** Returns the ISO date string of the Monday for the week containing `date`. */
export function getWeekStart(date: Date = new Date()): string {
  const d = new Date(date);
  const day = d.getDay(); // 0 = Sunday, 1 = Monday, ...
  const diff = day === 0 ? -6 : 1 - day;
  d.setDate(d.getDate() + diff);
  return formatDate(d);
}

/** Returns the Monday ISO date string of the next week. */
export function getNextWeekStart(weekStart: string): string {
  const d = parseDate(weekStart);
  d.setDate(d.getDate() + 7);
  return formatDate(d);
}

/** Returns the Monday ISO date string of the previous week. */
export function getPrevWeekStart(weekStart: string): string {
  const d = parseDate(weekStart);
  d.setDate(d.getDate() - 7);
  return formatDate(d);
}

/** Format a Date as YYYY-MM-DD */
export function formatDate(d: Date): string {
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/** Parse a YYYY-MM-DD date string into a local Date (noon to avoid DST edge cases). */
export function parseDate(iso: string): Date {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d, 12, 0, 0);
}

/** Human-readable week label, e.g. "Mar 9 – 15, 2026" */
export function formatWeekLabel(weekStart: string): string {
  const start = parseDate(weekStart);
  const end = new Date(start);
  end.setDate(end.getDate() + 6);

  const monthNames = [
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
  ];

  const startMonth = monthNames[start.getMonth()];
  const endMonth = monthNames[end.getMonth()];

  if (start.getMonth() === end.getMonth()) {
    return `${startMonth} ${start.getDate()} – ${end.getDate()}, ${start.getFullYear()}`;
  }
  return `${startMonth} ${start.getDate()} – ${endMonth} ${end.getDate()}, ${end.getFullYear()}`;
}

/** Check if a weekStart is the current week. */
export function isCurrentWeek(weekStart: string): boolean {
  return weekStart === getWeekStart();
}

/** Check if a weekStart is in the past. */
export function isPastWeek(weekStart: string): boolean {
  return weekStart < getWeekStart();
}

/** Check if a weekStart is in the future beyond the next week. */
export function isFutureWeek(weekStart: string): boolean {
  const currentWeek = getWeekStart();
  const nextWeek = getNextWeekStart(currentWeek);
  return weekStart > nextWeek;
}

/**
 * Check if plan creation is allowed for the given week.
 * Plans can only be created for the current week or next week.
 */
export function isCreateAllowedForWeek(weekStart: string): boolean {
  const currentWeek = getWeekStart();
  const nextWeek = getNextWeekStart(currentWeek);
  return weekStart === currentWeek || weekStart === nextWeek;
}

/** Validate that a string is a valid Monday date. */
export function isValidMonday(weekStart: string): boolean {
  const d = parseDate(weekStart);
  return d.getDay() === 1 && formatDate(d) === weekStart;
}
