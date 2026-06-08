import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import api from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { 
  ArrowLeft, 
  Trophy, 
  Target, 
  TrendingUp, 
  TrendingDown, 
  CheckCircle2, 
  AlertCircle,
  Cpu,
  Building2,
  UserCheck,
  Download,
  Share2
} from 'lucide-react';
import { useToast } from '@/hooks/use-toast';

interface FeedbackReport {
  overallScore: number;
  executiveSummary: string;
  personaScores: {
    persona: string;
    score: number;
    comment: string;
  }[];
  strengths: string[];
  weaknesses: string[];
}

export default function InterviewReport() {
  const { sessionId } = useParams();
  const navigate = useNavigate();
  const { toast } = useToast();
  
  const [report, setReport] = useState<FeedbackReport | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (sessionId) fetchReport();
  }, [sessionId]);

  const fetchReport = async () => {
    try {
      const res = await api.get(`/api/v1/interviews/${sessionId}/report`);
      setReport(res.data.data.feedback);
    } catch (error) {
      toast({ title: 'Error', description: 'Failed to load report.', variant: 'destructive' });
    } finally {
      setIsLoading(false);
    }
  };

  const getPersonaIcon = (persona: string) => {
    switch (persona) {
      case 'TECH_LEAD': return <Cpu className="h-5 w-5 text-indigo-500" />;
      case 'HIRING_MANAGER': return <Building2 className="h-5 w-5 text-amber-500" />;
      case 'HR_AGENT': return <UserCheck className="h-5 w-5 text-emerald-500" />;
      default: return <Target className="h-5 w-5 text-primary" />;
    }
  };

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-muted/30">
        <div className="flex flex-col items-center gap-4">
          <div className="h-12 w-12 border-4 border-primary border-t-transparent rounded-full animate-spin" />
          <p className="text-muted-foreground font-medium">Analyzing your performance data...</p>
        </div>
      </div>
    );
  }

  if (!report) return null;

  return (
    <div className="min-h-screen bg-muted/30 pb-20">
      <div className="bg-background border-b sticky top-0 z-10">
        <div className="container mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Button variant="ghost" size="icon" onClick={() => navigate('/dashboard')}>
              <ArrowLeft className="h-5 w-5" />
            </Button>
            <h1 className="text-xl font-bold">Interview Performance Report</h1>
          </div>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" className="gap-2">
              <Download className="h-4 w-4" /> Export PDF
            </Button>
            <Button variant="outline" size="sm" className="gap-2">
              <Share2 className="h-4 w-4" /> Share
            </Button>
          </div>
        </div>
      </div>

      <div className="container mx-auto px-4 py-8 max-w-5xl">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
          
          {/* Main Score & Summary */}
          <div className="lg:col-span-12">
            <Card className="bg-gradient-to-br from-slate-900 to-slate-800 text-white border-none shadow-2xl overflow-hidden relative">
              <div className="absolute top-0 right-0 p-8 opacity-10">
                <Trophy className="h-40 w-40" />
              </div>
              <CardContent className="p-10 flex flex-col md:flex-row items-center gap-12 relative z-10">
                <div className="relative h-48 w-48 flex items-center justify-center">
                  <svg className="h-full w-full -rotate-90">
                    <circle cx="96" cy="96" r="88" stroke="currentColor" strokeWidth="12" fill="transparent" className="text-white/10" />
                    <circle 
                      cx="96" cy="96" r="88" stroke="currentColor" strokeWidth="12" fill="transparent" 
                      className="text-primary stroke-primary" 
                      strokeDasharray={2 * Math.PI * 88} 
                      strokeDashoffset={2 * Math.PI * 88 * (1 - report.overallScore / 100)} 
                      strokeLinecap="round"
                    />
                  </svg>
                  <div className="absolute flex flex-col items-center">
                    <span className="text-6xl font-black">{report.overallScore}</span>
                    <span className="text-xs font-bold uppercase tracking-widest opacity-60">Percentile</span>
                  </div>
                </div>
                <div className="flex-1 space-y-6 text-center md:text-left">
                  <div>
                    <Badge className="mb-4 bg-primary/20 text-primary border-none hover:bg-primary/20">Executive Summary</Badge>
                    <h2 className="text-3xl font-bold mb-4">You did exceptionally well!</h2>
                    <p className="text-slate-300 text-lg leading-relaxed max-w-2xl">
                      {report.executiveSummary}
                    </p>
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>

          {/* Persona Breakdown */}
          <div className="lg:col-span-8 space-y-6">
            <h3 className="text-lg font-bold flex items-center gap-2 uppercase tracking-widest text-muted-foreground">
              <UserCheck className="h-4 w-4" /> Agent Feedback
            </h3>
            {report.personaScores.map((ps, i) => (
              <Card key={i} className="hover:shadow-md transition-shadow">
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-4">
                  <div className="flex items-center gap-3">
                    <div className="h-10 w-10 rounded-xl bg-muted flex items-center justify-center">
                      {getPersonaIcon(ps.persona)}
                    </div>
                    <div>
                      <CardTitle className="text-sm font-bold uppercase tracking-wider">{ps.persona.replace('_', ' ')}</CardTitle>
                      <CardDescription>Target Evaluation</CardDescription>
                    </div>
                  </div>
                  <div className="text-right">
                    <span className="text-2xl font-black text-primary">{ps.score}</span>
                    <span className="text-[10px] block font-bold text-muted-foreground uppercase">Score</span>
                  </div>
                </CardHeader>
                <CardContent>
                  <p className="text-sm text-slate-700 leading-relaxed bg-muted/30 p-4 rounded-xl italic">
                    "{ps.comment}"
                  </p>
                </CardContent>
              </Card>
            ))}
          </div>

          {/* Strengths & Weaknesses */}
          <div className="lg:col-span-4 space-y-6">
            <div className="space-y-6">
              <div>
                <h3 className="text-sm font-bold flex items-center gap-2 uppercase tracking-widest text-emerald-600 mb-4">
                  <TrendingUp className="h-4 w-4" /> Key Strengths
                </h3>
                <div className="space-y-3">
                  {report.strengths.map((s, i) => (
                    <div key={i} className="flex gap-3 p-3 bg-emerald-500/5 border border-emerald-500/10 rounded-lg text-sm text-emerald-900">
                      <CheckCircle2 className="h-5 w-5 text-emerald-500 shrink-0" />
                      {s}
                    </div>
                  ))}
                </div>
              </div>

              <div>
                <h3 className="text-sm font-bold flex items-center gap-2 uppercase tracking-widest text-rose-600 mb-4">
                  <TrendingDown className="h-4 w-4" /> Areas for Growth
                </h3>
                <div className="space-y-3">
                  {report.weaknesses.map((w, i) => (
                    <div key={i} className="flex gap-3 p-3 bg-rose-500/5 border border-rose-500/10 rounded-lg text-sm text-rose-900">
                      <AlertCircle className="h-5 w-5 text-rose-500 shrink-0" />
                      {w}
                    </div>
                  ))}
                </div>
              </div>
            </div>

            <Card className="bg-primary/5 border-primary/20">
              <CardHeader>
                <CardTitle className="text-sm font-bold flex items-center gap-2">
                  <Target className="h-4 w-4 text-primary" /> Next Steps
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <p className="text-xs text-muted-foreground">
                  Based on your performance, we recommend focusing on system design trade-offs before your next attempt.
                </p>
                <Button className="w-full" size="sm" onClick={() => navigate('/ai/mock-interview')}>
                  Start New Simulation
                </Button>
              </CardContent>
            </Card>
          </div>

        </div>
      </div>
    </div>
  );
}
