import { NewsDetailPage } from './features/news-detail/NewsDetailPage'
import { Header } from './components/Header'

export default function App() {
  if (/^\/news\/\d+$/.test(window.location.pathname)) {
    return <NewsDetailPage />
  }

  return <Header />
}
