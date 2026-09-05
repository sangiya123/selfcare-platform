import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from 'sonner';
import Layout from '@/components/Layout';
import ProtectedRoute from '@/components/ProtectedRoute';
import DashboardPage from '@/pages/DashboardPage';
import PageBuilderPage from '@/pages/PageBuilderPage';
import ThemeDesignerPage from '@/pages/ThemeDesignerPage';
import JourneyBuilderPage from '@/pages/JourneyBuilderPage';
import IntegrationBuilderPage from '@/pages/IntegrationBuilderPage';
import FeatureFlagPage from '@/pages/FeatureFlagPage';
import ReportBuilderPage from '@/pages/ReportBuilderPage';
import AIStudioPage from '@/pages/AIStudioPage';
import TenantsPage from '@/pages/TenantsPage';
import InsurancePage from '@/pages/InsurancePage';
import BrandingPage from '@/pages/BrandingPage';
import IndustryPacksPage from '@/pages/IndustryPacksPage';
import ChangeGovernancePage from '@/pages/ChangeGovernancePage';
import SettingsPage from '@/pages/SettingsPage';
import LoginPage from '@/pages/LoginPage';
import ContentPage from '@/pages/ContentPage';
import NotificationsPage from '@/pages/NotificationsPage';
import ProductMappingPage from '@/pages/ProductMappingPage';
import UsersPage from '@/pages/UsersPage';
import AssetManagerPage from '@/pages/AssetManagerPage';
import NavigationPage from '@/pages/NavigationPage';
import './globals.css';

const queryClient = new QueryClient();

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />

          <Route
            path="/"
            element={
              <ProtectedRoute>
                <Layout>
                  <DashboardPage />
                </Layout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/tenants"
            element={
              <ProtectedRoute>
                <Layout>
                  <TenantsPage />
                </Layout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/clients"
            element={
              <ProtectedRoute>
                <Layout>
                  <BrandingPage />
                </Layout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/pages"
            element={
              <ProtectedRoute>
                <Layout>
                  <PageBuilderPage />
                </Layout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/theme"
            element={
              <ProtectedRoute>
                <Layout>
                  <ThemeDesignerPage />
                </Layout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/journeys"
            element={
              <ProtectedRoute>
                <Layout>
                  <JourneyBuilderPage />
                </Layout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/integrations"
            element={
              <ProtectedRoute>
                <Layout>
                  <IntegrationBuilderPage />
                </Layout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/flags"
            element={
              <ProtectedRoute>
                <Layout>
                  <FeatureFlagPage />
                </Layout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/reports"
            element={
              <ProtectedRoute>
                <Layout>
                  <ReportBuilderPage />
                </Layout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/ai"
            element={
              <ProtectedRoute>
                <Layout>
                  <AIStudioPage />
                </Layout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/insurance"
            element={
              <ProtectedRoute>
                <Layout>
                  <InsurancePage />
                </Layout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/industry-packs"
            element={
              <ProtectedRoute>
                <Layout>
                  <IndustryPacksPage />
                </Layout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/changes"
            element={
              <ProtectedRoute>
                <Layout>
                  <ChangeGovernancePage />
                </Layout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/settings"
            element={
              <ProtectedRoute>
                <Layout>
                  <SettingsPage />
                </Layout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/content"
            element={
              <ProtectedRoute>
                <Layout>
                  <ContentPage />
                </Layout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/notifications"
            element={
              <ProtectedRoute>
                <Layout>
                  <NotificationsPage />
                </Layout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/products"
            element={
              <ProtectedRoute>
                <Layout>
                  <ProductMappingPage />
                </Layout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/users"
            element={
              <ProtectedRoute>
                <Layout>
                  <UsersPage />
                </Layout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/assets"
            element={
              <ProtectedRoute>
                <Layout>
                  <AssetManagerPage />
                </Layout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/navigation"
            element={
              <ProtectedRoute>
                <Layout>
                  <NavigationPage />
                </Layout>
              </ProtectedRoute>
            }
          />

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
        <Toaster richColors position="top-right" />
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>
);
