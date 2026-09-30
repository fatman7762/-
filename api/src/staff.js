/** @type {readonly string[]} */
export const STAFF_NAMES = Object.freeze([
  '野坂',
  '伊藤',
  '梁瀬',
  '中原',
  '坂本',
  '合田',
  'その他',
]);

/** Maps staff display name → Slack mention env var key */
export const STAFF_MENTION_ENV = Object.freeze({
  野坂: 'SLACK_MENTION_NOSAKA',
  伊藤: 'SLACK_MENTION_ITO',
  梁瀬: 'SLACK_MENTION_YANASE',
  中原: 'SLACK_MENTION_NAKAHARA',
  坂本: 'SLACK_MENTION_SAKAMOTO',
  合田: 'SLACK_MENTION_AIDA',
});

/**
 * Layout 編集で担当者名を変えられるため、空でない文字列を許可する。
 * @param {unknown} name
 * @returns {name is string}
 */
export function isValidStaffName(name) {
  return typeof name === 'string' && name.trim().length > 0 && name.trim().length <= 40;
}

/**
 * @param {unknown} size
 * @returns {size is number}
 */
export function isValidPartySize(size) {
  return Number.isInteger(size) && size >= 1 && size <= 6;
}
