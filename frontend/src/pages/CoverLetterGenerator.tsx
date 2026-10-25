import { AiFeedback } from '@/components/ai/AiFeedback';
import { Button } from '@/components/ui/button';
import { Card,CardContent,CardDescription,CardHeader,CardTitle } from '@/components/ui/card';
import { useToast } from '@/hooks/use-toast';
import api from '@/lib/api';
import { Job,Resume } from '@/types';
import {
ArrowLeft,
Copy,
Download,
Languages,
Loader2,
Sparkles,
Zap
} from 'lucide-react';
import { useEffect,useState } from 'react';
import { useNavigate } from 'react-router-dom';

export default function CoverLetterGenerator() {
  const navigate = useNavigate();
  const { toast } = useToast();
  
  const [resumes, setResumes] = useState<Resume[]>([]);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [selectedResumeId, setSelectedResumeId] = useState<string>('');
  const [selectedJobId, setSelectedJobId] = useState<string>('');
  
  const [isLoading, setIsLoading] = useState(false);
  const [coverLetter, setCoverLetter] = useState('');
  const [highlights, setHighlights] = useState<string[]>([]);
  const [aiLogId, setAiLogId] = useState<string | null>(null);

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

  const handleGenerate = async () => {
    if (!selectedResumeId || !selectedJobId) {
      toast({ title: 'Error', description: 'Please select both a resume and a job.', variant: 'destructive' });
      return;
    }

    setIsLoading(true);
    try {
      const job = jobs.find(j => j.id === selectedJobId);
      const response = await api.post('/api/v1/ai/cover-letter', {
        resumeId: selectedResumeId,
        jobText: job?.rawText,
        companyName: job?.company,
        position: job?.title
      });
      setCoverLetter(response.data.data.coverLetter);
      setHighlights(response.data.data.highlights || []);
      setAiLogId(response.data.data.aiLogId);
      toast({ title: 'Success', description: 'Cover letter generated!' });
    } catch (err: any) {
      toast({ title: 'Error', description: 'Generation failed.', variant: 'destructive' });
    } finally {
      setIsLoading(false);
    }
  };

  const copyToClipboard = () => {
    navigator.clipboard.writeText(coverLetter);
    toast({ title: 'Copied', description: 'Cover letter copied to clipboard.' });
  };

  return (
    <div className="min-h-screen bg-muted/30 pb-20">
      <div className="bg-background border-b sticky top-0 z-10">
        <div className="container mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Button variant="ghost" size="icon" onClick={() => navigate('/dashboard')}>
              <ArrowLeft className="h-5 w-5" />
            </Button>
            <h1 className="text-xl font-bold">AI Cover Letter Generator</h1>
          </div>
          {coverLetter && (
            <div className="flex gap-2">
              <Button variant="outline" size="sm" onClick={copyToClipboard}>
                <Copy className="mr-2 h-4 w-4" /> Copy
              </Button>
              <Button size="sm">
                <Download className="mr-2 h-4 w-4" /> Download PDF
              </Button>
            </div>
          )}
        </div>
      </div>

      <div className="container mx-auto px-4 py-8 max-w-6xl">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
          
          <div className="lg:col-span-4 space-y-6">
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">Configuration</CardTitle>
                <CardDescription>Select documents to generate your letter.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <label className="text-sm font-semibold text-muted-foreground">Resume Basis</label>
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
                  <label className="text-sm font-semibold text-muted-foreground">Target Job</label>
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
                </div>

                <div className="space-y-2 pt-4">
                  <label className="text-sm font-semibold text-muted-foreground">Style & Tone</label>
                  <div className="grid grid-cols-2 gap-2">
                    <Button variant="outline" size="sm" className="bg-primary/5 border-primary/20">Professional</Button>
                    <Button variant="outline" size="sm">Confident</Button>
                  </div>
                </div>

                <Button 
                  onClick={handleGenerate} 
                  className="w-full mt-4 h-11" 
                  disabled={isLoading || !selectedResumeId || !selectedJobId}
                >
                  {isLoading ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      Crafting Letter...
                    </>
                  ) : (
                    <>
                      <Sparkles className="mr-2 h-4 w-4" />
                      Generate Letter
                    </>
                  )}
                </Button>
              </CardContent>
            </Card>

            {highlights.length > 0 && (
              <Card>
                <CardHeader className="pb-2">
                  <CardTitle className="text-sm font-bold flex items-center gap-2">
                    <Zap className="h-4 w-4 text-primary" /> AI Key Highlights
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <ul className="space-y-2">
                    {highlights.map((h, i) => (
                      <li key={i} className="text-xs text-muted-foreground flex gap-2">
                        <div className="h-1 w-1 bg-primary rounded-full mt-1.5 shrink-0" />
                        {h}
                      </li>
                    ))}
                  </ul>
                </CardContent>
              </Card>
            )}
          </div>

          <div className="lg:col-span-8">
            {!coverLetter && !isLoading ? (
              <div className="h-full min-h-[500px] flex flex-col items-center justify-center bg-background rounded-xl border-2 border-dashed p-12 text-center">
                <div className="h-20 w-20 rounded-full bg-primary/10 flex items-center justify-center mb-6">
                  <Languages className="h-10 w-10 text-primary" />
                </div>
                <h2 className="text-2xl font-bold mb-2">Generate Your Cover Letter</h2>
                <p className="text-muted-foreground max-w-md">
                  We'll use your resume and the job requirements to draft a highly personalized, impactful cover letter in seconds.
                </p>
              </div>
            ) : isLoading ? (
              <div className="h-full min-h-[500px] flex flex-col items-center justify-center bg-background rounded-xl border p-12 space-y-6">
                <Loader2 className="h-10 w-10 animate-spin text-primary" />
                <p className="text-muted-foreground">Mapping your skills to the job requirements...</p>
              </div>
            ) : (
              <Card className="min-h-full">
                <CardContent className="p-12">
                  <div className="prose prose-sm max-w-none">
                    <div className="whitespace-pre-wrap font-serif text-lg leading-relaxed text-slate-800">
                      {coverLetter}
                    </div>
                  </div>
                  {aiLogId && <AiFeedback aiLogId={aiLogId} />}
                </CardContent>
              </Card>
            )}
          </div>

        </div>
      </div>
    </div>
  );
}

