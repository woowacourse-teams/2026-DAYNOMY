const me = '/api/users/me';
const shared = `${me}/shared-portfolio`;
const portfolio = `${me}/portfolio`;
const publicInvestor = (id) => `/api/league/investors/${encodeURIComponent(id)}`;
const positiveId = (id) => Number.isSafeInteger(id) && id > 0;
const list = (value) => Array.isArray(value);

// Names are also metric labels; never put account IDs or tokens in them.
export const OPERATIONS = [
  'weeks',
  'rankings',
  'investor',
  'details',
  'daily-history',
  'profile',
  'create-profile',
  'save-profile',
  'publication',
  'source',
  'shared',
  'import',
  'visibility',
  'remove',
  'decisions',
  'decision',
  'reason',
  'review',
  'revise-review',
  'follow',
  'unfollow',
  'follow-summary',
  'private-investor',
  'private-details',
  'private-history',
  'recalculate',
];
export const CASES = [
  ...OPERATIONS.filter((name) => !name.startsWith('private-')),
  'browse',
  'onboarding',
  'records',
  'follow-flow',
  'privacy',
  'mixed',
  'contention',
  'mixed-with-recalculate',
];
export const WRITE_CASES = CASES.filter(
  (name) =>
    ![
      'weeks',
      'rankings',
      'investor',
      'details',
      'daily-history',
      'profile',
      'source',
      'shared',
      'decisions',
      'follow-summary',
      'browse',
    ].includes(name),
);

export function runCase(name, context) {
  const { call, actor, target, config, index, phase } = context;
  const own = publicInvestor(actor.publicId);
  const url = publicInvestor(target.publicId);
  const holding = actor.holdings[0];
  const key = `lt601-${config.run}-${phase}-${index}`;
  const reason = { ...holding.decision, reason: `loadtest ${index}` };
  const review = {
    actualResult: 'loadtest result',
    differenceFromExpectation: 'loadtest difference',
    nextAction: 'loadtest action',
  };
  const get = (operation, path, valid, user = actor, status = 200) =>
    call(operation, 'GET', path, null, user, status, valid);
  const write = (operation, method, path, body, valid = () => true, status = 200) =>
    call(operation, method, path, body, actor, status, valid);
  const profile = (body) => body?.publicId === actor.publicId;
  const holdings = (body) => list(body?.holdings);
  const decisions = (body) => list(body?.transactions);
  const decision = (body) => positiveId(body?.id) && body.assetId === holding.assetId;
  const summary = () =>
    get('follow-summary', `${me}/league/follows/summary`, (body) => list(body?.investors));
  const publication = (value) =>
    write(
      'publication',
      'PUT',
      `${portfolio}/publication`,
      value,
      (body) =>
        profile(body) && Object.entries(value).every(([key, expected]) => body[key] === expected),
    );
  const importHoldings = () =>
    write(
      'import',
      'POST',
      `${shared}/import`,
      {
        holdings: actor.holdings.map(({ assetId, quantity, averagePurchasePrice, decision }) => ({
          assetId,
          quantity,
          averagePurchasePrice,
          decision,
        })),
        overwriteExisting: true,
      },
      (body) =>
        holdings(body) &&
        actor.holdings.every((expected) =>
          body.holdings.some(
            (actual) =>
              actual.assetId === expected.assetId &&
              actual.quantity === expected.quantity &&
              actual.averagePurchasePrice === expected.averagePurchasePrice,
          ),
        ),
    );
  const record = () =>
    write(
      'decision',
      'POST',
      `${portfolio}/decisions`,
      {
        requestKey: key,
        assetId: holding.assetId,
        decision: reason,
      },
      (body) => decision(body) && body.reason === reason.reason,
      201,
    );
  const addReview = (id) =>
    write(
      'review',
      'POST',
      `${portfolio}/decisions/${id}/reviews`,
      review,
      (body) => positiveId(body?.id) && body.actualResult === review.actualResult,
      201,
    );
  const reviseReview = (id, reviewId) =>
    write(
      'revise-review',
      'PUT',
      `${portfolio}/decisions/${id}/reviews/${reviewId}`,
      review,
      (body) => body?.id === reviewId && body.actualResult === review.actualResult,
    );
  const follow = () =>
    write(
      'follow',
      'POST',
      `${me}/league/follows/${encodeURIComponent(target.publicId)}`,
      null,
      () => true,
      204,
    );
  const unfollow = () =>
    write(
      'unfollow',
      'DELETE',
      `${me}/league/follows/${encodeURIComponent(target.publicId)}`,
      null,
      () => true,
      204,
    );

  const cases = {
    weeks: () =>
      get(
        'weeks',
        '/api/league/weeks',
        (body) =>
          list(body?.weeks) && body.weeks.some((week) => week.weekStart === config.weekStart),
        null,
      ),
    rankings: () =>
      get(
        'rankings',
        `/api/league/rankings?leagueType=${config.leagueType}` +
          `&weekStart=${config.weekStart}&page=${config.page}&size=20`,
        (body) => {
          const remaining = Math.max(0, body?.totalCount - config.page * 20);
          return (
            body?.weekStart === config.weekStart &&
            body.leagueType === config.leagueType &&
            body.confirmed === config.expectedConfirmed &&
            Number.isSafeInteger(body.totalCount) &&
            body.totalCount > config.page * 20 &&
            list(body.rankings) &&
            body.rankings.length === Math.min(20, remaining) &&
            (name !== 'rankings' || body.totalCount === config.expectedTotal) &&
            body.rankings.every(
              (entry) =>
                positiveId(entry.rank) &&
                typeof entry.publicId === 'string' &&
                typeof entry.weeklyReturnRate === 'number' &&
                Number.isFinite(entry.weeklyReturnRate),
            )
          );
        },
        config.authenticated ? actor : null,
      ),
    investor: () =>
      get(
        'investor',
        url,
        (body) =>
          body?.publicId === target.publicId &&
          list(body.history) &&
          body.performance &&
          typeof body.followed === 'boolean',
        config.authenticated ? actor : null,
      ),
    details: () =>
      get(
        'details',
        `${url}/details`,
        (body) =>
          body?.publicId === target.publicId &&
          list(body.holdings) &&
          list(body.decisions) &&
          body.holdings.length === target.holdingCount,
        null,
      ),
    'daily-history': () =>
      get(
        'daily-history',
        `${url}/daily-history?weekStart=${config.weekStart}`,
        (body) =>
          body?.weekStart === config.weekStart &&
          list(body.days) &&
          body.days.length === target.dailyPointCount,
        null,
      ),
    profile: () => get('profile', `${me}/investor-profile`, profile),
    'create-profile': () =>
      write(
        'create-profile',
        'PUT',
        `${me}/investor-profile`,
        actor.profile,
        (body) =>
          typeof body?.publicId === 'string' &&
          body.publicId.length > 0 &&
          body.displayName === actor.profile.displayName,
      ),
    'save-profile': () =>
      write(
        'save-profile',
        'PUT',
        `${me}/investor-profile`,
        actor.profile,
        (body) => profile(body) && body.displayName === actor.profile.displayName,
      ),
    publication: () => {
      publication({ ...actor.publication, leagueEnabled: !actor.publication.leagueEnabled });
      publication(actor.publication);
    },
    source: () => get('source', portfolio, (body) => list(body?.holdings)),
    shared: () => get('shared', shared, holdings),
    import: importHoldings,
    visibility: () => {
      for (const hidden of [true, false]) {
        write(
          'visibility',
          'PATCH',
          `${shared}/holdings/${holding.assetId}/visibility`,
          { hidden },
          (body) =>
            holdings(body) &&
            body.holdings.some((h) => h.assetId === holding.assetId && h.hidden === hidden),
        );
        get(
          'details',
          `${own}/details`,
          (body) =>
            body?.publicId === actor.publicId &&
            list(body.holdings) &&
            body.holdings.some((h) => h.assetName === holding.assetName) === !hidden,
          null,
        );
      }
    },
    remove: () => {
      write(
        'remove',
        'DELETE',
        `${shared}/holdings/${holding.assetId}`,
        null,
        (body) => holdings(body) && body.holdings.every((h) => h.assetId !== holding.assetId),
      );
      importHoldings();
    },
    decisions: () => get('decisions', `${portfolio}/transactions`, decisions),
    decision: () => {
      const first = record();
      const replay = record();
      if (first.id !== replay.id) throw new Error('Decision replay created a duplicate');
      const body = cases.decisions();
      if (body.transactions.filter((item) => item.id === first.id).length !== 1)
        throw new Error('Decision was not stored once');
      return first;
    },
    reason: () =>
      write(
        'reason',
        'PUT',
        `${portfolio}/decisions/${actor.decisionId}/reason`,
        { reason: reason.reason },
        (body) => body?.id === actor.decisionId && body.reason === reason.reason,
      ),
    review: () => addReview(actor.decisionId),
    'revise-review': () => reviseReview(actor.decisionId, actor.reviewId),
    follow: () => {
      follow();
      if (!summary().investors.some((item) => item.publicId === target.publicId))
        throw new Error('Follow missing');
    },
    unfollow: () => {
      unfollow();
      if (summary().investors.some((item) => item.publicId === target.publicId))
        throw new Error('Unfollow missing');
    },
    'follow-summary': summary,
    browse: () => {
      cases.weeks();
      cases.rankings();
      cases.investor();
      cases.details();
      cases['daily-history']();
    },
    onboarding: () => {
      cases['save-profile']();
      const snapshot = (body) =>
        JSON.stringify(
          body.holdings
            .map(({ assetId, quantity, averagePurchasePrice }) => ({
              assetId,
              quantity,
              averagePurchasePrice,
            }))
            .sort((left, right) => left.assetId - right.assetId),
        );
      const source = snapshot(cases.source());
      importHoldings();
      cases.decision();
      publication(actor.publication);
      cases.profile();
      cases.shared();
      if (snapshot(cases.source()) !== source) throw new Error('Original portfolio changed');
    },
    records: () => {
      const created = cases.decision();
      const added = addReview(created.id);
      reviseReview(created.id, added.id);
      const body = cases.decisions();
      if (
        !body.transactions.some(
          (item) =>
            item.id === created.id &&
            item.reviews?.some(
              (value) => value.id === added.id && value.actualResult === review.actualResult,
            ),
        )
      ) {
        throw new Error('Review was not stored');
      }
    },
    'follow-flow': () => {
      cases.unfollow();
      cases.follow();
      cases.follow();
      cases.unfollow();
    },
    privacy: () => {
      // Disabling participation changes its history; restore the DB baseline after this run.
      publication({
        profilePublic: false,
        leagueEnabled: false,
        allocationPublic: false,
        detailPublic: false,
      });
      try {
        for (const [operation, path] of [
          ['private-investor', own],
          ['private-details', `${own}/details`],
          ['private-history', `${own}/daily-history?weekStart=${config.weekStart}`],
        ])
          get(operation, path, () => true, null, 404);
      } finally {
        publication(actor.publication);
      }
    },
    mixed: () => {
      // Initial mix: 70% browsing, 10% follows, 10% records, 10% publication changes.
      return cases[
        index % 10 < 7 ? 'browse' : ['follow-flow', 'records', 'privacy'][(index % 10) - 7]
      ]();
    },
    contention: () => cases.decision(),
  };
  if (!cases[name]) throw new Error('Unsupported league case');
  return cases[name]();
}
