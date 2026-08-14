import { RealEstateLoanRulePage } from './features/news/newslist/RealEstateLoanRulePage'
import { NewsListPage } from './features/news/newslist/NewsListPage'

function App() {
  if (window.location.pathname === '/news/real-estate-loan-rule') {
    return <RealEstateLoanRulePage />
  }

  return <NewsListPage />
}

export default App
