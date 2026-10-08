import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/dashboard/DashboardPage';
import TradingPage from './pages/trading/TradingPage';
import FundPage from './pages/fund/FundPage';
import RiskPage from './pages/risk/RiskPage';
import IntradayPage from './pages/risk/IntradayPage';
import VarPage from './pages/risk/VarPage';
import SurveillancePage from './pages/surveillance/SurveillancePage';
import MonitorPage from './pages/monitor/MonitorPage';
import PortfolioPage from './pages/portfolio/PortfolioPage';
import SettlementPage from './pages/settlement/SettlementPage';
import GatewayPage from './pages/gateway/GatewayPage';
import StrategyPage from './pages/strategy/StrategyPage';
import RiskBoardPage from './pages/strategy/RiskBoardPage';
import BacktestPage from './pages/backtest/BacktestPage';
import DegradePage from './pages/degrade/DegradePage';

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<Layout />}>
            <Route index element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/trading" element={<TradingPage />} />
            <Route path="/fund" element={<FundPage />} />
            <Route path="/risk" element={<RiskPage />} />
            <Route path="/intraday" element={<IntradayPage />} />
            <Route path="/var" element={<VarPage />} />
            <Route path="/surveillance" element={<SurveillancePage />} />
            <Route path="/portfolio" element={<PortfolioPage />} />
            <Route path="/settlement" element={<SettlementPage />} />
            <Route path="/gateway" element={<GatewayPage />} />
            <Route path="/monitor" element={<MonitorPage />} />
            <Route path="/strategy" element={<StrategyPage />} />
            <Route path="/backtest" element={<BacktestPage />} />
            <Route path="/risk-board" element={<RiskBoardPage />} />
            <Route path="/degrade" element={<DegradePage />} />
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
