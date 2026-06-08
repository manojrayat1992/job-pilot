import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import api from '@/lib/api';
import { ApplicationStats, JobApplication } from '@/types';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import {
  Briefcase,
  Plus,
  FileText,
  Sparkles,
  Search,
  Brain,
  ShieldCheck,
  TrendingUp,
  Calendar,
  Target,
  Zap,
  DollarSign,
  Linkedin,
  Mail,
  HeartHandshake,
  GitCompare,
  ChevronRight,
  Lock
} from 'lucide-react';
import { motion } from 'framer-motion';
import { PageLoader } from '@/components/loading/PageLoader';
import { useApiCall } from '@/hooks/useApiCall';
import { useToast } from '@/hooks/use-toast';

export default function Dashboard() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { toast } = useToast();
  const { user, setAuth } = useAuthStore();
  const [stats, setStats] = useState<ApplicationStats | null>(null);
  const [recentApplications, setRecentApplications] = useState<JobApplication[]>([]);
  const { execute, isLoading } = useApiCall();

  useEffect(() => {
    fetchDashboardData();
    
    if (searchParams.get('success') === 'true') {
      toast({
        title: 'Payment Successful! 🎉',
        description: 'You are now a PRO member with unlimited access.',
      });
      // Try to re-fetch user profile to update role, or just optimistically update for now
      if (user) {
        setAuth({ ...user, role: 'PRO' }, useAuthStore.getState().accessToken, useAuthStore.getState().refreshToken);
      }
      // Remove query param
      navigate('/dashboard', { replace: true });
    }
  }, [searchParams]);

  const fetchDashboardData = async () => {
    await execute(
      async () => {
        const [statsRes, appsRes] = await Promise.all([
          api.get('/api/v1/applications/stats'),
          api.get('/api/v1/applications?page=0&size=5'),
        ]);
        setStats(statsRes.data.data);
        setRecentApplications(appsRes.data.data.content);
      },
      { errorMessage: 'Failed to load dashboard data. Please try again.' }
    );
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'SAVED': return 'bg-slate-100 text-slate-600 border-slate-200';
      case 'APPLIED': return 'bg-blue-50 text-blue-600 border-blue-100';
      case 'INTERVIEWING': return 'bg-amber-50 text-amber-600 border-amber-100';
      case 'OFFER': return 'bg-emerald-50 text-emerald-600 border-emerald-100';
      case 'REJECTED': return 'bg-rose-50 text-rose-600 border-rose-100';
      default: return 'bg-slate-50 text-slate-500';
    }
  };

  const container = {
    hidden: { opacity: 0 },
    show: {
      opacity: 1,
      transition: {
        staggerChildren: 0.1
      }
    }
  };

  const item = {
    hidden: { y: 20, opacity: 0 },
    show: { y: 0, opacity: 1 }
  };

  if (isLoading) {
    return <PageLoader message="Initializing Command Center..." fullScreen={false} />;
  }

  return (
    <motion.div
      variants={container}
      initial="hidden"
      animate="show"
      className="space-y-10 pb-20"
    >
      {/* Hero Welcome Section */}
      <section className="relative overflow-hidden rounded-[2.5rem] bg-slate-900 p-8 md:p-12 text-white shadow-2xl">
        <div className="relative z-10 space-y-6 max-w-2xl">
          <motion.div variants={item} className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/10 border border-white/10 text-xs font-bold tracking-widest uppercase text-indigo-300">
            <Zap className="h-3 w-3" /> System Active
          </motion.div>
          <motion.h1 variants={item} className="text-4xl md:text-5xl font-black tracking-tight leading-tight">
            Welcome back, <span className="text-transparent bg-clip-text bg-gradient-to-r from-indigo-400 to-emerald-400">{user?.name?.split(' ')[0]}</span>.
          </motion.h1>
          <motion.p variants={item} className="text-lg text-slate-400 font-medium">
            Your career trajectory is looking strong. You have {stats?.byStatus?.['INTERVIEW'] || 0} interviews lined up this week.
          </motion.p>
          <motion.div variants={item} className="flex flex-wrap gap-4 pt-4">
            <Button
              onClick={() => navigate('/applications/new')}
              className="h-12 px-8 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-bold transition-all hover:shadow-lg hover:shadow-indigo-500/20"
            >
              <Plus className="mr-2 h-5 w-5" /> New Application
            </Button>
            <Button
              variant="outline"
              onClick={() => navigate('/ai/job-research')}
              className="h-12 px-8 rounded-xl border-white/10 bg-white/5 hover:bg-white/10 text-white font-bold"
            >
              Scout Market
            </Button>
          </motion.div>
        </div>

        {/* Background Decorative Elements */}
        <div className="absolute top-0 right-0 -mr-20 -mt-20 h-96 w-96 bg-indigo-600/20 rounded-full blur-[100px]" />
        <div className="absolute bottom-0 left-0 -ml-20 -mb-20 h-64 w-64 bg-emerald-500/10 rounded-full blur-[80px]" />
        <TrendingUp className="absolute right-12 bottom-12 h-64 w-64 text-white/[0.03] -rotate-12 pointer-events-none" />
      </section>

      {/* Stats Grid */}
      <motion.div variants={item} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {[
          { label: 'Total Tracked', val: stats?.total || 0, icon: Briefcase, color: 'text-blue-600', bg: 'bg-blue-50' },
          { label: 'Active Applications', val: stats?.byStatus?.['APPLIED'] || 0, icon: FileText, color: 'text-indigo-600', bg: 'bg-indigo-50' },
          { label: 'Interview Stage', val: (stats?.byStatus?.['INTERVIEW'] || 0) + (stats?.byStatus?.['PHONE_SCREEN'] || 0), icon: Target, color: 'text-amber-600', bg: 'bg-amber-50' },
          { label: 'Offers Secured', val: stats?.byStatus?.['OFFER'] || 0, icon: Sparkles, color: 'text-emerald-600', bg: 'bg-emerald-50' },
        ].map((stat, i) => (
          <Card key={i} className="border-slate-100 shadow-sm hover:shadow-md transition-shadow rounded-3xl overflow-hidden group">
            <CardContent className="p-6">
              <div className="flex items-center justify-between">
                <div className={`p-3 rounded-2xl ${stat.bg} ${stat.color} group-hover:scale-110 transition-transform`}>
                  <stat.icon className="h-6 w-6" />
                </div>
                <div className="text-right">
                  <p className="text-sm font-bold text-slate-400 uppercase tracking-wider">{stat.label}</p>
                  <h3 className="text-3xl font-black text-slate-900">{stat.val}</h3>
                </div>
              </div>
            </CardContent>
          </Card>
        ))}
      </motion.div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Main AI Tools Suite */}
        <div className="lg:col-span-2 space-y-6">
          <div className="flex items-center justify-between px-2">
            <h3 className="text-xl font-black text-slate-900 flex items-center gap-2">
              <Sparkles className="h-5 w-5 text-indigo-600" /> AI Intelligence Suite
            </h3>
            <Badge variant="outline" className="rounded-full border-slate-200 text-slate-500 font-bold">PREMIUM ACCESS</Badge>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {[
              {
                title: 'Agent Command Center',
                desc: '6 AI agents working together on your career goals',
                icon: Target,
                path: '/ai/agents',
                color: 'bg-gradient-to-br from-indigo-50 to-violet-50 text-indigo-600',
                label: 'NEW'
              },
              {
                title: 'One-Click Copilot',
                desc: 'Smart browser extension for 1-click applications',
                icon: Zap,
                path: '/ai/copilot',
                color: 'bg-indigo-50 text-indigo-600',
                label: 'ASSIST'
              },
              {
                title: 'Resume Optimizer',
                desc: 'Tailor your CV for specific job descriptions',
                icon: FileText,
                path: '/ai/resume-optimizer',
                color: 'bg-blue-50 text-blue-600',
                label: 'CRAFT'
              },
              {
                title: 'ATS Compatibility',
                desc: 'Simulate bot scans and fix formatting',
                icon: ShieldCheck,
                path: '/ai/ats-checker',
                color: 'bg-emerald-50 text-emerald-600',
                label: 'SCAN'
              },
              {
                title: 'Mock Interview',
                desc: 'Practice with a panel of AI agents',
                icon: Brain,
                path: '/ai/mock-interview',
                color: 'bg-violet-50 text-violet-600',
                label: 'TRAIN'
              },
              {
                title: 'Job Researcher',
                desc: 'Automated market scouting agent',
                icon: Search,
                path: '/ai/job-research',
                color: 'bg-sky-50 text-sky-600',
                label: 'SCOUT'
              },
              {
                title: 'Cover Letter',
                desc: 'Generate unique letters in seconds',
                icon: Sparkles,
                path: '/ai/cover-letter',
                color: 'bg-amber-50 text-amber-600',
                label: 'WRITE'
              },
              {
                title: 'Interview Prep',
                desc: 'Master common behavioral questions',
                icon: Briefcase,
                path: '/ai/interview-prep',
                color: 'bg-rose-50 text-rose-600',
                label: 'PREP'
              },
              {
                title: 'Salary Negotiator',
                desc: 'Get data-driven counter-offers',
                icon: DollarSign,
                path: '/ai/salary',
                color: 'bg-emerald-50 text-emerald-600',
                label: 'VALUE',
                isPremium: true
              },
              {
                title: 'LinkedIn Optimizer',
                desc: 'Attract recruiters with SEO profiles',
                icon: Linkedin,
                path: '/ai/linkedin',
                color: 'bg-blue-50 text-blue-600',
                label: 'BRAND',
                isPremium: true
              },
              {
                title: 'Email Sequences',
                desc: 'Generate 5-step outreach campaigns',
                icon: Mail,
                path: '/ai/outreach-sequence',
                color: 'bg-violet-50 text-violet-600',
                label: 'NETWORK',
                isPremium: true
              },
              {
                title: 'Culture Fit Score',
                desc: 'Analyze company values alignment',
                icon: HeartHandshake,
                path: '/ai/culture-fit',
                color: 'bg-amber-50 text-amber-600',
                label: 'ASSESS',
                isPremium: true
              },
              {
                title: 'Resume Diff',
                desc: 'Compare resume version changes',
                icon: GitCompare,
                path: '/ai/resume-diff',
                color: 'bg-slate-50 text-slate-600',
                label: 'DIFF',
                isPremium: false
              }
            ].map((tool, i) => {
              const isLocked = tool.isPremium && user?.role !== 'PRO';
              
              return (
              <Card
                key={i}
                onClick={() => isLocked ? navigate('/pricing') : navigate(tool.path)}
                className={`group cursor-pointer border-slate-100 shadow-sm transition-all rounded-[2rem] overflow-hidden relative ${isLocked ? 'hover:border-amber-200 hover:shadow-amber-500/10' : 'hover:border-indigo-200 hover:shadow-indigo-500/5 hover:shadow-xl'}`}
              >
                {isLocked && (
                  <div className="absolute top-4 right-4 text-amber-500 bg-amber-50 p-2 rounded-full z-10">
                    <Lock className="h-4 w-4" />
                  </div>
                )}
                <CardContent className={`p-6 flex items-start gap-4 ${isLocked ? 'opacity-70 grayscale-[0.5]' : ''}`}>
                  <div className={`p-4 rounded-2xl transition-transform group-hover:scale-110 ${tool.color}`}>
                    <tool.icon className="h-6 w-6" />
                  </div>
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="text-[10px] font-black tracking-widest opacity-40">{tool.label}</span>
                      <h4 className="font-bold text-slate-900 group-hover:text-indigo-600 transition-colors">{tool.title}</h4>
                    </div>
                    <p className="text-sm text-slate-500 leading-relaxed">{tool.desc}</p>
                  </div>
                </CardContent>
              </Card>
            )})}
          </div>
        </div>

        {/* Sidebar: Recent Activity */}
        <div className="space-y-6">
          <div className="flex items-center justify-between px-2">
            <h3 className="text-xl font-black text-slate-900 flex items-center gap-2">
              <Calendar className="h-5 w-5 text-indigo-600" /> Pipeline
            </h3>
            <Button variant="ghost" size="sm" onClick={() => navigate('/applications')} className="text-xs font-bold text-indigo-600 hover:bg-indigo-50">View All</Button>
          </div>

          <Card className="border-slate-100 shadow-sm rounded-[2rem] overflow-hidden">
            <CardContent className="p-4">
              {recentApplications.length === 0 ? (
                <div className="text-center py-12 space-y-3">
                  <div className="h-16 w-16 bg-slate-50 rounded-full flex items-center justify-center mx-auto text-slate-300">
                    <Briefcase className="h-8 w-8" />
                  </div>
                  <p className="text-sm font-medium text-slate-400">No active applications in the pipeline.</p>
                </div>
              ) : (
                <div className="space-y-3">
                  {recentApplications.map((app) => (
                    <div
                      key={app.id}
                      onClick={() => navigate(`/applications/${app.id}`)}
                      className="group p-4 rounded-2xl border border-transparent hover:border-slate-100 hover:bg-slate-50 transition-all cursor-pointer flex items-center justify-between"
                    >
                      <div className="flex items-center gap-4">
                        <div className="h-10 w-10 bg-white border border-slate-100 rounded-xl flex items-center justify-center font-black text-slate-400 text-xs shadow-sm group-hover:border-indigo-100 group-hover:text-indigo-600 transition-colors">
                          {app.company.charAt(0)}
                        </div>
                        <div>
                          <h4 className="font-bold text-sm text-slate-900 group-hover:text-indigo-600 transition-colors">{app.position}</h4>
                          <p className="text-xs text-slate-500 font-medium">{app.company}</p>
                        </div>
                      </div>
                      <Badge className={`rounded-lg border px-2 py-0.5 text-[10px] font-black uppercase tracking-wider shadow-none ${getStatusColor(app.status)}`}>
                        {app.status}
                      </Badge>
                    </div>
                  ))}
                  <Button
                    variant="ghost"
                    className="w-full h-12 rounded-xl text-slate-400 hover:text-indigo-600 hover:bg-indigo-50 group font-bold text-sm"
                    onClick={() => navigate('/applications')}
                  >
                    Manage Pipeline <ChevronRight className="ml-2 h-4 w-4 group-hover:translate-x-1 transition-transform" />
                  </Button>
                </div>
              )}
            </CardContent>
          </Card>

          {/* Quick Tip Card */}
          <Card className="bg-gradient-to-br from-indigo-600 to-violet-700 text-white border-none rounded-[2rem] overflow-hidden shadow-xl shadow-indigo-200">
            <CardContent className="p-6 space-y-4 relative">
              <div className="relative z-10 space-y-4">
                <div className="h-10 w-10 bg-white/20 rounded-xl flex items-center justify-center">
                  <Brain className="h-5 w-5 text-white" />
                </div>
                <h4 className="font-bold leading-tight">Pro Tip: Optimize your resume for every application to increase interview rates by 40%.</h4>
                <Button className="w-full bg-white text-indigo-600 hover:bg-indigo-50 font-black rounded-xl h-11 border-none shadow-lg">
                  TRY OPTIMIZER
                </Button>
              </div>
              <Sparkles className="absolute right-0 bottom-0 h-32 w-32 text-white/5 -mb-8 -mr-8" />
            </CardContent>
          </Card>
        </div>
      </div>
    </motion.div>
  );
}
