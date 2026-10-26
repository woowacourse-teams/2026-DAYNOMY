export const USER_HOSTNAME = 'daynomy.com';
export const STAFF_HOSTNAME = 'staff.daynomy.com';

export function isUserHostname(hostname: string) {
  return hostname.toLowerCase() === USER_HOSTNAME;
}

export function isStaffHostname(hostname: string) {
  return hostname.toLowerCase() === STAFF_HOSTNAME;
}
