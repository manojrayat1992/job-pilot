import { AiFeedback } from '@/components/ai/AiFeedback';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card,CardContent,CardDescription,CardHeader,CardTitle } from '@/components/ui/card';
import { Tabs,TabsContent,TabsList,TabsTrigger } from '@/components/ui/tabs';
import { useToast } from '@/hooks/use-toast';
import api from '@/lib/api';
import { Job,Resume } from '@/types';
import {
ArrowLeft,
CheckCircle2,
ChevronRight,
History,
Loader2,
Sparkles,
TrendingUp
} from 'lucide-react';
import { useEffect,useState } from 'react';
import { useNavigate } from 'react-router-dom';

interface OptimizeResult {
  optimizedResume: any;
  changes: string[];
  keywordsAdded: string[];
  missingKeywords: string[];
  matchScore: number;
  scoreImprovement: string;
  aiLogId: string;
}

export default function ResumeOptimizer() {
  const navigate = useNavigate();
  const { toast } = useToast();

  const [resumes, setResumes] = useState<Resume[]>([]);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [selectedResumeId, setSelectedResumeId] = useState<string>('');
  const [selectedJobId, setSelectedJobId] = useState<string>('');
  const [jobDescription, setJobDescription] = useState<string>('');
  const [useRawJobDescription, setUseRawJobDescription] = useState<boolean>(false);

  const [isLoading, setIsLoading] = useState(false);
  const [resultContext, setResultContext] = useState<{ resumeId: string; jobId?: string; jobDescription?: string } | null>(null);
  const [result, setResult] = useState<OptimizeResult | null>(null);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      const [resumesRes, jobsRes] = await Promise.all([
        api.get('/api/v1/resumes'),
        api.get('/api/v1/jobs')
      ]);
      setResumes(resumesRes.data.data.content);
      setJobs(jobsRes.data.data.content);
    } catch (error) {
      toast({ title: 'Error', description: 'Failed to fetch data.', variant: 'destructive' });
    }
  };

  const handleOptimize = async () => {
    if (!selectedResumeId || (!useRawJobDescription && !selectedJobId) || (useRawJobDescription && !jobDescription.trim())) {
      toast({ title: 'Error', description: 'Please select a resume and provide a job.', variant: 'destructive' });
      return;
    }

    setIsLoading(true);
    setResult(null);
    setResultContext(null);
    const context = { resumeId: selectedResumeId, jobId: useRawJobDescription ? undefined : selectedJobId, jobDescription: useRawJobDescription ? jobDescription : undefined };
    try {
      const payload: any = {
        resumeId: selectedResumeId,
      };
      if (useRawJobDescription) {
        payload.jobText = jobDescription;
      } else {
        payload.jobId = selectedJobId;
      }

      const response = await api.post('/api/v1/ai/optimize-resume', payload);
      setResult(response.data.data);
      setResultContext(context);
      toast({ title: 'Success', description: 'Resume optimized successfully!' });
    } catch (err: any) {
      toast({ title: 'Error', description: 'Optimization failed.', variant: 'destructive' });
    } finally {
      setIsLoading(false);
    }
  };

  const handleSaveAsNew = async () => {
    if (!result || !resultContext) return;
    setIsLoading(true);
    try {
      const response = await api.post(`/api/v1/resumes/${resultContext.resumeId}/drafts`, {
        jobId: resultContext.jobId,
        jobDescription: resultContext.jobDescription,
        proposedResume: result.optimizedResume,
      });
      navigate(`/resumes?source=${resultContext.resumeId}&draft=${response.data.data.id}`);
    } catch (error) {
      toast({ title: 'Draft not saved', description: 'The proposed resume must preserve your original facts and supported format. Generate a draft from Resume Hub to retry.', variant: 'destructive' });
    } finally {
      setIsLoading(false);
    }
  };

  const renderGauge = (score: number) => {
    const color = score > 80 ? 'text-green-500' : score > 60 ? 'text-yellow-500' : 'text-red-500';
    const strokeDasharray = `${score}, 100`;

    return (
      <div className="relative h-32 w-32 mx-auto">
        <svg viewBox="0 0 36 36" className="h-full w-full">
          <path
            className="stroke-muted fill-none"
            strokeWidth="3"
            d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
          />
          <path
            className={`${color} fill-none transition-all duration-1000 ease-out`}
            strokeWidth="3"
            strokeDasharray={strokeDasharray}
            strokeLinecap="round"
            d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
          />
        </svg>
        <div className="absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-3xl font-bold">{score}%</span>
          <span className="text-[10px] uppercase text-muted-foreground font-semibold">Match</span>
        </div>
      </div>
    );
  };

  return (
    <div className="min-h-screen bg-muted/30 pb-20">
      <div className="bg-background border-b sticky top-0 z-10">
        <div className="container mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Button variant="ghost" size="icon" onClick={() => navigate('/dashboard')}>
              <ArrowLeft className="h-5 w-5" />
            </Button>
            <h1 className="text-xl font-bold">AI Resume Optimizer</h1>
          </div>
          <div className="flex gap-2">
            <Button variant="outline" size="sm">
              <History className="mr-2 h-4 w-4" /> History
            </Button>
            {result && (
              <Button size="sm" onClick={handleSaveAsNew} disabled={isLoading}>
                <Sparkles className="mr-2 h-4 w-4" /> Review and Save Draft
              </Button>
            )}
          </div>
        </div>
      </div>

      <div className="container mx-auto px-4 py-8 max-w-6xl">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">

          {/* Left Panel: Configuration */}
          <div className="lg:col-span-4 space-y-6">
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">Step 1: Selection</CardTitle>
                <CardDescription>Select the source resume and target job.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <label className="text-sm font-semibold text-muted-foreground">Source Resume</label>
                  <select
                    className="w-full p-2 rounded-md border bg-background text-sm outline-none focus:ring-2 focus:ring-primary/20"
                    value={selectedResumeId}
                    onChange={(e) => setSelectedResumeId(e.target.value)}
                  >
                    <option value="">Select a resume...</option>
                    {resumes.map(r => (
                      <option key={r.id} value={r.id}>{r.title}</option>
                    ))}
                  </select>
                </div>

                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <label className="text-sm font-semibold text-muted-foreground">Target Job Description</label>
                    <button
                      type="button"
                      className="text-xs text-primary hover:underline font-semibold"
                      onClick={() => setUseRawJobDescription(!useRawJobDescription)}
                    >
                      {useRawJobDescription ? "Select Saved Job" : "Paste Job Description"}
                    </button>
                  </div>
                  {useRawJobDescription ? (
                    <textarea
                      placeholder="Paste the full job description here..."
                      className="w-full p-2.5 rounded-md border bg-background text-sm outline-none focus:ring-2 focus:ring-primary/20 min-h-[120px] resize-none"
                      value={jobDescription}
                      onChange={(e) => setJobDescription(e.target.value)}
                    />
                  ) : (
                    <select
                      className="w-full p-2 rounded-md border bg-background text-sm outline-none focus:ring-2 focus:ring-primary/20"
                      value={selectedJobId}
                      onChange={(e) => setSelectedJobId(e.target.value)}
                    >
                      <option value="">Select a job...</option>
                      {jobs.map(j => (
                        <option key={j.id} value={j.id}>{j.company} - {j.title}</option>
                      ))}
                    </select>
                  )}
                </div>

                <Button
                  onClick={handleOptimize}
                  className="w-full mt-4 h-11"
                  disabled={isLoading || !selectedResumeId || (!useRawJobDescription ? !selectedJobId : !jobDescription.trim())}
                >
                  {isLoading ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      Optimizing with RAG...
                    </>
                  ) : (
                    <>
                      <Sparkles className="mr-2 h-4 w-4" />
                      Optimize Now
                    </>
                  )}
                </Button>
              </CardContent>
            </Card>

            {result && (
              <Card className="bg-primary/5 border-primary/20">
                <CardContent className="pt-6">
                  <div className="text-center space-y-4">
                    {renderGauge(result.matchScore)}
                    <div>
                      <h3 className="font-bold text-lg">Score Improved!</h3>
                      <p className="text-sm text-primary font-medium">{result.scoreImprovement}</p>
                    </div>
                    <div className="flex justify-center gap-4 border-t pt-4 border-primary/10">
                      <div className="text-center">
                        <div className="text-xs text-muted-foreground uppercase">Keywords</div>
                        <div className="font-bold">+{result.keywordsAdded.length}</div>
                      </div>
                      <div className="w-px bg-primary/10 h-8" />
                      <div className="text-center">
                        <div className="text-xs text-muted-foreground uppercase">Changes</div>
                        <div className="font-bold">{result.changes.length}</div>
                      </div>
                    </div>
                  </div>
                </CardContent>
              </Card>
            )}
          </div>

          {/* Right Panel: Results */}
          <div className="lg:col-span-8">
            {!result && !isLoading ? (
              <div className="h-full min-h-[400px] flex flex-col items-center justify-center bg-background rounded-xl border-2 border-dashed p-12 text-center">
                <div className="h-20 w-20 rounded-full bg-primary/10 flex items-center justify-center mb-6">
                  <Sparkles className="h-10 w-10 text-primary" />
                </div>
                <h2 className="text-2xl font-bold mb-2">Ready to Optimize</h2>
                <p className="text-muted-foreground max-w-md">
                  Select your resume and a job description to see AI-powered suggestions tailored specifically to the role's requirements.
                </p>
              </div>
            ) : isLoading ? (
              <div className="h-full min-h-[400px] flex flex-col items-center justify-center bg-background rounded-xl border p-12 space-y-6">
                <div className="flex gap-2">
                  <div className="h-3 w-3 bg-primary rounded-full animate-bounce [animation-delay:-0.3s]"></div>
                  <div className="h-3 w-3 bg-primary rounded-full animate-bounce [animation-delay:-0.15s]"></div>
                  <div className="h-3 w-3 bg-primary rounded-full animate-bounce"></div>
                </div>
                <div className="text-center space-y-2">
                  <h3 className="font-bold text-xl">Analyzing Requirements</h3>
                  <p className="text-muted-foreground animate-pulse">Running semantic search on your past successes...</p>
                </div>
              </div>
            ) : (
              <div className="space-y-6">
                <Tabs defaultValue="overview" className="w-full">
                  <TabsList className="grid w-full grid-cols-3 h-12">
                    <TabsTrigger value="overview">Analysis Overview</TabsTrigger>
                    <TabsTrigger value="content">Optimized Content</TabsTrigger>
                    <TabsTrigger value="diff">Highlight Changes</TabsTrigger>
                  </TabsList>

                  <TabsContent value="overview" className="mt-6 space-y-6">
                    <Card>
                      <CardHeader>
                        <CardTitle className="flex items-center gap-2">
                          <TrendingUp className="h-5 w-5 text-green-500" />
                          Key Enhancements
                        </CardTitle>
                      </CardHeader>
                      <CardContent>
                        <ul className="space-y-3">
                          {result?.changes.map((change, i) => (
                            <li key={i} className="flex gap-3 text-sm p-3 bg-muted/50 rounded-lg">
                              <CheckCircle2 className="h-4 w-4 text-primary shrink-0 mt-0.5" />
                              {change}
                            </li>
                          ))}
                        </ul>
                      </CardContent>
                    </Card>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                      <Card>
                        <CardHeader className="pb-3">
                          <CardTitle className="text-sm font-semibold uppercase text-muted-foreground">
                            Keywords Added
                          </CardTitle>
                        </CardHeader>
                        <CardContent>
                          <div className="flex flex-wrap gap-2">
                            {result?.keywordsAdded.map((kw, i) => (
                              <Badge key={i} variant="secondary" className="bg-green-500/10 text-green-700 hover:bg-green-500/20 border-green-500/20">
                                {kw}
                              </Badge>
                            ))}
                            {result?.keywordsAdded?.length === 0 && <span className="text-sm text-muted-foreground">None identified.</span>}
                          </div>
                        </CardContent>
                      </Card>

                      <Card>
                        <CardHeader className="pb-3">
                          <CardTitle className="text-sm font-semibold uppercase text-muted-foreground">
                            Missing Skills
                          </CardTitle>
                        </CardHeader>
                        <CardContent>
                          <div className="flex flex-wrap gap-2">
                            {result?.missingKeywords.map((kw, i) => (
                              <Badge key={i} variant="outline" className="border-yellow-500/30 text-yellow-700 bg-yellow-500/5">
                                {kw}
                              </Badge>
                            ))}
                            {result?.missingKeywords.length === 0 && <span className="text-sm text-muted-foreground">Great! No gaps found.</span>}
                          </div>
                        </CardContent>
                      </Card>
                    </div>

                    {result?.aiLogId && (
                      <AiFeedback aiLogId={result.aiLogId} />
                    )}
                  </TabsContent>

                  <TabsContent value="content" className="mt-6">
                    <Card>
                      <CardHeader>
                        <CardTitle>Optimized Draft</CardTitle>
                        <CardDescription>Click sections to edit manually before saving.</CardDescription>
                      </CardHeader>
                      <CardContent className="space-y-6">
                        <div className="space-y-2">
                          <Label className="text-xs uppercase font-bold text-primary">Professional Summary</Label>
                          <div className="p-4 bg-muted/30 rounded-lg text-sm leading-relaxed border italic">
                            "{result?.optimizedResume.summary}"
                          </div>
                        </div>

                        <div className="space-y-4">
                          <Label className="text-xs uppercase font-bold text-primary">Experience Highlights</Label>
                          {result?.optimizedResume.experience?.map((exp: any, i: number) => (
                            <div key={i} className="space-y-2 pb-4 border-b last:border-0">
                              <div className="flex justify-between items-start">
                                <h4 className="font-bold">{exp.title}</h4>
                                <span className="text-xs text-muted-foreground">{exp.company}</span>
                              </div>
                              <ul className="space-y-2">
                                {exp.bullets?.map((bullet: string, bi: number) => (
                                  <li key={bi} className="flex gap-2 text-sm">
                                    <ChevronRight className="h-4 w-4 text-primary shrink-0 mt-0.5" />
                                    {bullet}
                                  </li>
                                ))}
                              </ul>
                            </div>
                          ))}
                        </div>
                      </CardContent>
                    </Card>
                  </TabsContent>

                  <TabsContent value="diff" className="mt-6">
                    <Card>
                      <CardHeader>
                        <CardTitle>Before vs After</CardTitle>
                        <CardDescription>Visualizing the AI's refinement strategy.</CardDescription>
                      </CardHeader>
                      <CardContent>
                        <div className="p-12 text-center bg-muted/20 rounded-xl border-2 border-dashed">
                          <Loader2 className="h-8 w-8 animate-spin mx-auto text-muted-foreground mb-4" />
                          <p className="text-sm text-muted-foreground">Diff visualization module loading...</p>
                        </div>
                      </CardContent>
                    </Card>
                  </TabsContent>
                </Tabs>
              </div>
            )}
          </div>

        </div>
      </div>
    </div>
  );
}

function Label({ children, className }: { children: React.ReactNode, className?: string }) {
  return <label className={`text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70 ${className}`}>{children}</label>;
}
