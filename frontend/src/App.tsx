import { Header } from './components/Header'
import { NewsDetailPage } from './features/news/newsdetail/NewsDetailPage.tsx'
import SearchPage from './features/search/SearchPage'

export default function App() {
  if (/^\/news\/\d+$/.test(window.location.pathname)) {
    return <NewsDetailPage />
  }

  if (window.location.pathname === '/search') return <SearchPage />

  return <Header />
}
