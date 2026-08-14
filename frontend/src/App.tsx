import { Header } from './components/Header'
import { NewsDetailPage } from './features/news/newsdetail/NewsDetailPage'
import { NewsListPage } from './features/news/newslist/NewsListPage'
import { RealEstateLoanRulePage } from './features/news/newslist/RealEstateLoanRulePage'
import SearchPage from './features/search/SearchPage'

export default function App() {
  const { pathname } = window.location

  if (/^\/news\/\d+$/.test(pathname)) {
    return <NewsDetailPage />
  }

  if (pathname === '/news/real-estate-loan-rule') {
    return <RealEstateLoanRulePage />
  }

  if (pathname === '/search') {
    return <SearchPage />
  }

  if (pathname === '/') {
    return <NewsListPage />
  }

  return <Header />
}
