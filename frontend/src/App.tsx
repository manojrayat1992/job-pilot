import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import AuthPage from '@/pages/AuthPage';
import Dashboard from '@/pages/Dashboard';
import Applications from '@/pages/Applications';
import Resumes from '@/pages/Resumes';
import Jobs from '@/pages/Jobs';
import ResumeOptimizer from '@/pages/ResumeOptimizer';
import CoverLetterGenerator from '@/pages/CoverLetterGenerator';
import InterviewPrep from '@/pages/InterviewPrep';
import JobResearcher from './pages/JobResearcher';
import NewApplication from '@/pages/NewApplication';
import ApplicationDetails from '@/pages/ApplicationDetails';
import MockInterview from '@/pages/MockInterview';
import InterviewReport from '@/pages/InterviewReport';
import AtsChecker from '@/pages/AtsChecker';
import Home from '@/pages/Home';
import Copilot from '@/pages/Copilot';
import ResumeBuilder from '@/pages/ResumeBuilder';
import Networking from '@/pages/Networking';
import Onboarding from '@/pages/Onboarding';
import AgentCommandCenter from '@/pages/AgentCommandCenter';
import SalaryNegotiator from '@/pages/SalaryNegotiator';
import LinkedInOptimizer from '@/pages/LinkedInOptimizer';
import EmailDripSequence from '@/pages/EmailDripSequence';
import CultureFit from '@/pages/CultureFit';
import ResumeDiff from '@/pages/ResumeDiff';
import UsageDashboard from '@/pages/UsageDashboard';
import Pricing from '@/pages/Pricing';
import ModelManagement from '@/pages/ModelManagement';
import ConversationHistory from '@/pages/ConversationHistory';

import Layout from '@/components/Layout';
import ErrorBoundary from '@/components/error/ErrorBoundary';
import { Toaster } from '@/components/ui/toaster';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { user } = useAuthStore();
  const location = window.location.pathname;
  
  if (!user) return <Navigate to="/login" />;
  
  if (!user.onboardingCompleted && location !== '/onboarding') {
    return <Navigate to="/onboarding" />;
  }
  
  if (user.onboardingCompleted && location === '/onboarding') {
    return <Navigate to="/dashboard" />;
  }

  return <Layout>{children}</Layout>;
}

function App() {
  return (
    <ErrorBoundary>
      <Router>
        <Routes>
          <Route path="/login" element={<AuthPage />} />
          <Route path="/register" element={<AuthPage />} />
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <Dashboard />
              </ProtectedRoute>
            }
          />
          <Route
            path="/applications"
            element={
              <ProtectedRoute>
                <Applications />
              </ProtectedRoute>
            }
          />
          <Route
            path="/applications/new"
            element={
              <ProtectedRoute>
                <NewApplication />
              </ProtectedRoute>
            }
          />
          <Route
            path="/applications/:id"
            element={
              <ProtectedRoute>
                <ApplicationDetails />
              </ProtectedRoute>
            }
          />
          <Route
            path="/resumes"
            element={
              <ProtectedRoute>
                <Resumes />
              </ProtectedRoute>
            }
          />
          <Route
            path="/resumes/builder"
            element={
              <ProtectedRoute>
                <ResumeBuilder />
              </ProtectedRoute>
            }
          />
          <Route
            path="/jobs"
            element={
              <ProtectedRoute>
                <Jobs />
              </ProtectedRoute>
            }
          />
          <Route
            path="/ai/resume-optimizer"
            element={
              <ProtectedRoute>
                <ResumeOptimizer />
              </ProtectedRoute>
            }
          />
          <Route
            path="/ai/cover-letter"
            element={
              <ProtectedRoute>
                <CoverLetterGenerator />
              </ProtectedRoute>
            }
          />
          <Route
            path="/ai/interview-prep"
            element={
              <ProtectedRoute>
                <InterviewPrep />
              </ProtectedRoute>
            }
          />
          <Route
            path="/ai/job-research"
            element={
              <ProtectedRoute>
                <JobResearcher />
              </ProtectedRoute>
            }
          />
          <Route
            path="/ai/mock-interview"
            element={
              <ProtectedRoute>
                <MockInterview />
              </ProtectedRoute>
            }
          />
          <Route
            path="/ai/mock-interview/:sessionId/report"
            element={
              <ProtectedRoute>
                <InterviewReport />
              </ProtectedRoute>
            }
          />
          <Route
            path="/ai/ats-checker"
            element={
              <ProtectedRoute>
                <AtsChecker />
              </ProtectedRoute>
            }
          />
          <Route
            path="/ai/copilot"
            element={
              <ProtectedRoute>
                <Copilot />
              </ProtectedRoute>
            }
          />
          <Route
            path="/networking"
            element={
              <ProtectedRoute>
                <Networking />
              </ProtectedRoute>
            }
          />
          <Route
            path="/ai/agents"
            element={
              <ProtectedRoute>
                <AgentCommandCenter />
              </ProtectedRoute>
            }
          />
          <Route
            path="/ai/salary"
            element={
              <ProtectedRoute>
                <SalaryNegotiator />
              </ProtectedRoute>
            }
          />
          <Route
            path="/ai/linkedin"
            element={
              <ProtectedRoute>
                <LinkedInOptimizer />
              </ProtectedRoute>
            }
          />
          <Route
            path="/ai/outreach-sequence"
            element={
              <ProtectedRoute>
                <EmailDripSequence />
              </ProtectedRoute>
            }
          />
          <Route
            path="/ai/culture-fit"
            element={
              <ProtectedRoute>
                <CultureFit />
              </ProtectedRoute>
            }
          />
          <Route
            path="/ai/resume-diff"
            element={
              <ProtectedRoute>
                <ResumeDiff />
              </ProtectedRoute>
            }
          />
          <Route
            path="/usage"
            element={
              <ProtectedRoute>
                <UsageDashboard />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/models"
            element={
              <ProtectedRoute>
                <ModelManagement />
              </ProtectedRoute>
            }
          />
          <Route
            path="/conversation-history"
            element={
              <ProtectedRoute>
                <ConversationHistory />
              </ProtectedRoute>
            }
          />
          <Route
            path="/pricing"
            element={
              <ProtectedRoute>
                <Pricing />
              </ProtectedRoute>
            }
          />
          <Route
            path="/onboarding"
            element={
              <ProtectedRoute>
                <Onboarding />
              </ProtectedRoute>
            }
          />
          <Route path="/" element={<Home />} />
        </Routes>
      </Router>
      <Toaster />
    </ErrorBoundary>
  );
}

export default App;
