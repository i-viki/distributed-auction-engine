/**
 * Utility formatters for Distributed Auction Engine
 */

export function formatCurrency(amount) {
  if (amount == null) return '$0.00';
  const num = typeof amount === 'number' ? amount : parseFloat(amount);
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(num);
}

function parseIsoDate(dateString) {
  if (!dateString) return null;
  return new Date(dateString);
}

export function formatDateTime(dateString) {
  if (!dateString) return '—';
  try {
    const d = parseIsoDate(dateString);
    return d.toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  } catch (e) {
    return dateString;
  }
}

export function getCountdown(endTime) {
  if (!endTime) return { totalMs: 0, hours: 0, minutes: 0, seconds: 0, isEnded: true, text: 'Ended' };
  
  const parsed = parseIsoDate(endTime);
  const end = parsed ? parsed.getTime() : 0;
  const now = Date.now();
  const diff = end - now;

  if (diff <= 0) {
    return { totalMs: 0, hours: 0, minutes: 0, seconds: 0, isEnded: true, text: 'Ended' };
  }

  const hours = Math.floor(diff / (1000 * 60 * 60));
  const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
  const seconds = Math.floor((diff % (1000 * 60)) / 1000);

  const pad = (n) => String(n).padStart(2, '0');
  const text = `${hours > 0 ? pad(hours) + ':' : ''}${pad(minutes)}:${pad(seconds)}`;

  return {
    totalMs: diff,
    hours,
    minutes,
    seconds,
    isEnded: false,
    isUrgent: diff < 60000, // less than 1 minute
    text,
  };
}

