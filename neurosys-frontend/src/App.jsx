import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { LabProvider } from './contexts/LabContext';
import { WebSocketProvider } from './contexts/WebSocketContext';
import Layout from './components/Layout';
import LandingPage from './pages/LandingPage';
import Login from './pages/Login';
import LabSelection from './pages/LabSelection';
import LabManagement from './pages/LabManagement';
import Dashboard from './pages/Dashboard';
import AdminLaptopPerformance from './pages/AdminLaptopPerformance';
import Computers from './pages/Computers';
import PendingComputers from './pages/PendingComputers';
import ComputerDetails from './pages/ComputerDetails';
import Alerts from './pages/Alerts';
import Analytics from './pages/Analytics';
import Settings from './pages/Settings';
import Software from './pages/Software';
import LabReadiness from './pages/LabReadiness';

const ProtectedRoute = ({ children }) => {
  const { isAuthenticated, loading } = useAuth();
  if (loading) return <div className="p-8 text-center text-slate-400 text-sm font-bold bg-slate-900 min-h-screen flex items-center justify-center">Initializing NeuroSys...</div>;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return children;
};

function AppRoutes() {
  return (
    <Routes>
      {/* 1. Public Landing Page at "/" - ALWAYS renders LandingPage */}
      <Route path="/" element={<LandingPage />} />

      {/* 2. Login Page at "/login" - ALWAYS renders Login page when clicked */}
      <Route path="/login" element={<Login />} />

      {/* 3. Post-Login Lab Selection Page */}
      <Route
        path="/select-lab"
        element={
          <ProtectedRoute>
            <LabProvider>
              <LabSelection />
            </LabProvider>
          </ProtectedRoute>
        }
      />

      {/* 4. Protected Dashboard & Admin Pages */}
      <Route
        element={
          <ProtectedRoute>
            <LabProvider>
              <WebSocketProvider>
                <Layout />
              </WebSocketProvider>
            </LabProvider>
          </ProtectedRoute>
        }
      >
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/labs" element={<LabManagement />} />
        <Route path="/admin-laptop" element={<AdminLaptopPerformance />} />
        <Route path="/computers" element={<Computers />} />
        <Route path="/software" element={<Software />} />
        <Route path="/lab-readiness" element={<LabReadiness />} />
        <Route path="/pending-computers" element={<PendingComputers />} />
        <Route path="/computers/:id" element={<ComputerDetails />} />
        <Route path="/alerts" element={<Alerts />} />
        <Route path="/analytics" element={<Analytics />} />
        <Route path="/settings" element={<Settings />} />
      </Route>

      {/* 5. Catch-all fallback route */}
      <Route 
        path="*" 
        element={<Navigate to="/" replace />} 
      />
    </Routes>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  );
}
