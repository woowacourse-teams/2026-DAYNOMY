import { NewsDetailPage } from './feature/news/newsdetail/NewsDetailPage.tsx'
import { Header } from './components/Header'

export default function App() {
  if (/^\/news\/\d+$/.test(window.location.pathname)) {
    return <NewsDetailPage />
  }

  return <Header />
}
