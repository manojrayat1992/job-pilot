import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '@/lib/api';
import { Resume } from '@/types';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { 
  ArrowLeft, 
  Loader2, 
  Search, 
  Sparkles, 
  Globe, 
  Database, 
  FileCheck,
  ExternalLink,
  ChevronRight,
  TrendingUp,
  Brain,
  Building2
} from 'lucide-react';
import { useToast } from '@/hooks/use-toast';

interface RankedJob {
  title: string;
  company: string;
  location: string;
  url: string;
  matchScore: number;
  matchReason: string;
  source: string;
  description: string;
}

interface ResearchResult {
  report: string;
  jobs: RankedJob[];
  thoughts: string[];
}

export default function JobResearcher() {
  const navigate = useNavigate();
  const { toast } = useToast();
  
  const [resumes, setResumes] = useState<Resume[]>([]);
  const [selectedResumeId, setSelectedResumeId] = useState<string>('');
  const [query, setQuery] = useState('');
  
  const [isLoading, setIsLoading] = useState(false);
  const [isApplying, setIsApplying] = useState(false);
  const [result, setResult] = useState<ResearchResult | null>(null);
  const [selectedJobs, setSelectedJobs] = useState<Set<number>>(new Set());

  useEffect(() => {
    fetchResumes();
  }, []);

  const fetchResumes = async () => {
    try {
      const res = await api.get('/api/v1/resumes');
      setResumes(res.data.data.content);
    } catch (error) {
      toast({ title: 'Error', description: 'Failed to fetch resumes.', variant: 'destructive' });
    }
  };

  const handleResearch = async () => {
    if (!query) {
      toast({ title: 'Error', description: 'Please enter a search query.', variant: 'destructive' });
      return;
    }

    setIsLoading(true);
    setResult(null);
    try {
      const response = await api.post('/api/v1/agents/job-research', {
        query,
        resumeId: selectedResumeId || undefined
      });
      setResult(response.data.data);
      toast({ title: 'Success', description: 'Agent completed the research task!' });
    } catch (err: any) {
      toast({ title: 'Error', description: 'Research task failed.', variant: 'destructive' });
    } finally {
      setIsLoading(false);
    }
  };

  const toggleJobSelection = (index: number) => {
    const newSelected = new Set(selectedJobs);
    if (newSelected.has(index)) {
      newSelected.delete(index);
    } else {
      newSelected.add(index);
    }
    setSelectedJobs(newSelected);
  };

  const selectAllJobs = () => {
    if (!result) return;
    if (selectedJobs.size === result.jobs.length) {
      setSelectedJobs(new Set());
    } else {
      setSelectedJobs(new Set(result.jobs.map((_, i) => i)));
    }
  };

  const handleAutoApply = async () => {
    if (selectedJobs.size === 0) {
      toast({ title: 'Error', description: 'Please select at least one job.', variant: 'destructive' });
      return;
    }

    setIsApplying(true);
    try {
      const selectedJobData = Array.from(selectedJobs).map(index => result!.jobs[index]);
      await api.post('/api/v1/agents/auto-apply', {
        jobs: selectedJobData,
        resumeId: selectedResumeId || undefined
      });
      toast({ title: 'Success', description: `Successfully applied to ${selectedJobs.size} jobs!` });
      setSelectedJobs(new Set());
    } catch (err: any) {
      toast({ title: 'Error', description: 'Auto-apply failed.', variant: 'destructive' });
    } finally {
      setIsApplying(false);
    }
  };

  return (
    <div className="min-h-screen bg-muted/30 pb-20">
      <div className="bg-background border-b sticky top-0 z-10">
        <div className="container mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Button variant="ghost" size="icon" onClick={() => navigate('/dashboard')}>
              <ArrowLeft className="h-5 w-5" />
            </Button>
            <h1 className="text-xl font-bold flex items-center gap-2">
              <Search className="h-5 w-5 text-primary" />
              Autonomous Job Researcher
            </h1>
          </div>
        </div>
      </div>

      <div className="container mx-auto px-4 py-8 max-w-6xl">
        {/* Search Header */}
        <div className="mb-12 text-center space-y-6">
          <div className="max-w-2xl mx-auto space-y-4">
            <h2 className="text-3xl font-bold tracking-tight">What kind of role are we looking for today?</h2>
            <p className="text-muted-foreground text-lg">
              The agent will search your saved jobs and the web. Found results are automatically saved to your <span className="text-primary font-semibold">Jobs</span> section.
            </p>
          </div>
          
          <div className="max-w-3xl mx-auto">
            <Card className="p-2 shadow-xl border-primary/20 ring-4 ring-primary/5">
              <div className="flex gap-2">
                <div className="flex-1 relative">
                  <Search className="absolute left-3 top-3 h-5 w-5 text-muted-foreground" />
                  <Input 
                    placeholder="e.g. Remote Backend Engineer role with Rust and Kubernetes..."
                    className="pl-10 h-12 border-none text-lg focus-visible:ring-0"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && handleResearch()}
                  />
                </div>
                <select 
                  className="w-48 bg-muted/50 rounded-md px-3 text-sm outline-none border-none focus:ring-1 focus:ring-primary/20"
                  value={selectedResumeId}
                  onChange={(e) => setSelectedResumeId(e.target.value)}
                >
                  <option value="">Personalize with...</option>
                  {resumes.map(r => (
                    <option key={r.id} value={r.id}>{r.title}</option>
                  ))}
                </select>
                <Button className="h-12 px-8 font-bold" onClick={handleResearch} disabled={isLoading}>
                  {isLoading ? <Loader2 className="h-5 w-5 animate-spin" /> : "Deploy Agent"}
                </Button>
              </div>
            </Card>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
          
          {/* Agent Thoughts / Process */}
          <div className="lg:col-span-4 space-y-6">
            <Card className="h-full">
              <CardHeader>
                <CardTitle className="text-sm font-bold uppercase tracking-widest text-muted-foreground flex items-center gap-2">
                  <Brain className="h-4 w-4" /> Agent Status
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="space-y-6 relative before:absolute before:left-2 before:top-2 before:bottom-2 before:w-0.5 before:bg-muted">
                  {!result && !isLoading && (
                    <p className="text-sm text-muted-foreground italic pl-6">Waiting for deployment...</p>
                  )}
                  {isLoading && (
                    <div className="flex gap-4 pl-6 relative">
                      <div className="absolute left-[-22px] top-1 h-4 w-4 rounded-full bg-primary animate-ping" />
                      <div className="space-y-2">
                        <p className="text-sm font-bold text-primary">Active Mission</p>
                        <p className="text-xs text-muted-foreground">The agent is currently searching internal databases and web listings...</p>
                      </div>
                    </div>
                  )}
                  {result?.thoughts.map((thought, i) => {
                    const isAction = thought.startsWith('Action:');
                    const isObservation = thought.startsWith('Observation:');
                    const isThought = thought.startsWith('Thought:');

                    return (
                      <div key={i} className="flex gap-4 pl-6 relative group">
                        <div className={`absolute left-[-22px] top-1 h-4 w-4 rounded-full border-4 border-background transition-all group-hover:scale-125 ${
                          isAction ? 'bg-amber-500' : isObservation ? 'bg-emerald-500' : 'bg-primary'
                        }`} />
                        <div className="space-y-1">
                          <p className={`text-sm ${isAction ? 'font-mono text-amber-600' : isThought ? 'italic text-slate-600' : 'font-medium'}`}>
                            {thought}
                          </p>
                          <p className="text-[10px] text-muted-foreground uppercase">{new Date().toLocaleTimeString()}</p>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </CardContent>
            </Card>
          </div>

          {/* Results Area */}
          <div className="lg:col-span-8 space-y-6">
            {!result && !isLoading ? (
              <div className="h-full min-h-[400px] flex flex-col items-center justify-center bg-background rounded-xl border-2 border-dashed p-12 text-center shadow-inner">
                <div className="h-20 w-20 bg-primary/5 rounded-full flex items-center justify-center mb-6">
                  <Globe className="h-10 w-10 text-primary opacity-40 animate-pulse" />
                </div>
                <h3 className="text-xl font-bold text-slate-800">Ready for Deployment</h3>
                <p className="text-sm text-slate-500 mt-2 max-w-sm mx-auto">
                  Enter your target role above. The agent will orchestrate a multi-step search across internal data and the open web.
                </p>
              </div>
            ) : isLoading ? (
              <div className="space-y-6">
                {[1, 2, 3].map(i => (
                  <Card key={i} className="animate-pulse border-none shadow-md overflow-hidden">
                    <div className="h-2 w-full bg-slate-200" />
                    <CardHeader className="h-24 bg-slate-50" />
                    <CardContent className="h-24 bg-white" />
                  </Card>
                ))}
              </div>
            ) : (
              <div className="space-y-10 animate-in fade-in slide-in-from-bottom-4 duration-700">
                {/* Executive Summary */}
                <div className="relative group">
                  <div className="absolute -inset-1 bg-gradient-to-r from-primary/20 to-indigo-500/20 rounded-3xl blur opacity-25 group-hover:opacity-50 transition duration-1000" />
                  <Card className="relative bg-white border-none shadow-2xl rounded-3xl overflow-hidden">
                    <div className="absolute top-0 right-0 p-6 opacity-5 rotate-12">
                      <Sparkles className="h-32 w-32" />
                    </div>
                    <CardHeader className="pb-2">
                      <CardTitle className="text-xs font-black uppercase tracking-[0.2em] text-primary flex items-center gap-2">
                        <Brain className="h-4 w-4" /> Agent Intelligence Report
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="p-8 pt-2">
                      <p className="text-xl leading-relaxed font-serif text-slate-800 italic">
                        "{result?.report}"
                      </p>
                    </CardContent>
                  </Card>
                </div>

                {/* Ranked Jobs */}
                <div className="space-y-6">
                  <div className="flex items-center justify-between px-2">
                    <h3 className="text-sm font-black uppercase tracking-widest text-slate-400 flex items-center gap-2">
                      <FileCheck className="h-4 w-4" /> Recommended Targets
                    </h3>
                    <div className="flex items-center gap-4">
                      <Button 
                        variant="outline" 
                        size="sm" 
                        className="text-[10px] font-black uppercase h-8 px-4 rounded-full border-slate-200"
                        onClick={selectAllJobs}
                      >
                        {selectedJobs.size === result?.jobs.length ? 'Deselect All' : 'Select All'}
                      </Button>
                      <Button 
                        size="sm" 
                        className="text-[10px] font-black uppercase h-8 px-4 rounded-full shadow-lg shadow-primary/20"
                        disabled={selectedJobs.size === 0 || isApplying}
                        onClick={handleAutoApply}
                      >
                        {isApplying ? <Loader2 className="h-3 w-3 animate-spin mr-2" /> : <Sparkles className="h-3 w-3 mr-2" />}
                        Auto Apply ({selectedJobs.size})
                      </Button>
                      <span className="text-[10px] font-bold text-slate-400">{result?.jobs?.length || 0} Matches Found</span>
                    </div>
                  </div>

                  {result?.jobs?.map((job, i) => (
                    <Card key={i} className={`group hover:shadow-2xl transition-all duration-500 border-none shadow-lg rounded-3xl overflow-hidden ${selectedJobs.has(i) ? 'ring-2 ring-primary ring-offset-2' : ''}`}>
                      <div className="h-1.5 w-full bg-gradient-to-r from-primary to-indigo-500 opacity-0 group-hover:opacity-100 transition-opacity" />
                      <CardContent className="p-8">
                        <div className="flex gap-6">
                          <div className="pt-2">
                            <input 
                              type="checkbox" 
                              className="h-5 w-5 rounded border-slate-300 text-primary focus:ring-primary transition-all cursor-pointer"
                              checked={selectedJobs.has(i)}
                              onChange={() => toggleJobSelection(i)}
                            />
                          </div>
                          <div className="flex flex-col md:flex-row justify-between items-start gap-6 flex-1">
                          <div className="space-y-4 flex-1">
                            <div className="space-y-1">
                              <div className="flex items-center gap-3">
                                <h4 className="text-2xl font-black text-slate-900 group-hover:text-primary transition-colors">{job.title}</h4>
                                <Badge variant="outline" className="text-[10px] h-6 px-3 rounded-full uppercase font-black tracking-tighter border-slate-200">
                                  {job.source === 'Internal' ? <Database className="h-3 w-3 mr-1 text-primary" /> : <Globe className="h-3 w-3 mr-1 text-indigo-500" />}
                                  {job.source}
                                </Badge>
                              </div>
                              <p className="text-lg text-slate-500 font-bold flex items-center gap-2">
                                <Building2 className="h-4 w-4 text-slate-400" /> {job.company} <span className="text-slate-300">•</span> {job.location}
                              </p>
                            </div>
                            
                            <div className="p-5 bg-slate-50 rounded-2xl text-sm leading-relaxed border border-slate-100 group-hover:bg-primary/5 group-hover:border-primary/10 transition-colors">
                              <p className="font-black text-primary text-[10px] uppercase tracking-widest mb-2 flex items-center gap-1">
                                <Sparkles className="h-3 w-3" /> Agent Strategic Fit
                              </p>
                              <span className="text-slate-700 font-medium">{job.matchReason}</span>
                            </div>
                          </div>

                          <div className="flex flex-col items-end justify-between self-stretch">
                            <div className="text-right bg-slate-50 p-4 rounded-2xl border border-slate-100">
                              <div className="text-3xl font-black text-primary leading-none">{job.matchScore}%</div>
                              <div className="text-[10px] uppercase font-black text-slate-400 mt-1 tracking-tighter">Confidence</div>
                            </div>
                            
                            <div className="flex gap-2 mt-6">
                              <Button 
                                variant="ghost" 
                                size="sm" 
                                className="rounded-xl text-slate-500 hover:text-primary hover:bg-primary/5"
                                onClick={() => window.open(job.url, '_blank')}
                              >
                                <ExternalLink className="h-4 w-4" />
                              </Button>
                              <Button 
                                size="sm" 
                                className="rounded-xl font-bold shadow-lg shadow-primary/20 bg-slate-900 hover:bg-primary transition-all px-6"
                                onClick={() => navigate('/applications/new', { 
                                  state: { 
                                    position: job.title, 
                                    company: job.company,
                                    source: job.source === 'Web' ? 'Agent Search' : 'Internal Database'
                                  } 
                                })}
                              >
                                Apply Now <ChevronRight className="ml-1 h-4 w-4" />
                              </Button>
                            </div>
                          </div>
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                  ))}
                </div>
              </div>
            )}
          </div>

        </div>
      </div>
    </div>
  );
}
