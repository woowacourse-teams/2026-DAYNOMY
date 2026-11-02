import { Navigate, useSearchParams } from 'react-router-dom';

export function LeagueReviewGuideDemoPage() {
  const [params] = useSearchParams();
  return <Navigate to={params.get('view') === 'reviews' ? '/league/reviews' : '/league'} replace />;
}
