export type InvestmentCalendarNotificationScope = 'PORTFOLIO' | 'WATCHLIST' | 'MAJOR_ECONOMIC';

export type InvestmentCalendarNotificationTiming =
  'SEVEN_DAYS_BEFORE' | 'ONE_DAY_BEFORE' | 'SAME_DAY_MORNING';

export type InvestmentCalendarNotificationSetting = {
  email: string;
  emailVerified: boolean;
  scopes: InvestmentCalendarNotificationScope[];
  timings: InvestmentCalendarNotificationTiming[];
  sendBeforeAnnouncement: boolean;
  sendAfterAnnouncement: boolean;
};

export type InvestmentCalendarNotificationUpdateRequest = Omit<
  InvestmentCalendarNotificationSetting,
  'emailVerified'
>;

export type NotificationEmailVerificationRequest = {
  email: string;
};
