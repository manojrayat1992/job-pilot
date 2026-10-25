import { useState, useEffect } from 'react';
import api from '@/lib/api';
import { Job } from '@/types';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Briefcase, Building, MapPin, Trash2, Zap, Eye, ExternalLink, DollarSign, Calendar, Sparkles } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "../components/ui/dialog";
import { Label } from '@/components/ui/label';

interface IndexTask { entity_id: string; kind: string; status: string; attempts: number }

export default function Jobs() {
  const [jobs, setJobs] = useState<Job[]>([]);
  const [indexTasks, setIndexTasks] = useState<IndexTask[]>([]);
  const [indexError, setIndexError] = useState(false);
  const [retryingIndex, setRetryingIndex] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [analyzing, setAnalyzing] = useState(false);
  const [jdText, setJdText] = useState('');
  const [jobUrl, setJobUrl] = useState('');
  const [jobTitle, setJobTitle] = useState('');
  const [open, setOpen] = useState(false);
  const [selectedJob, setSelectedJob] = useState<Job | null>(null);
  const { toast } = useToast();

  useEffect(() => {
    fetchJobs();
    fetchIndex();
    const timer = window.setInterval(fetchIndex, 10000);
    return () => window.clearInterval(timer);
  }, []);

  const fetchJobs = async () => {
    try {
      const res = await api.get('/api/v1/jobs');
      setJobs(res.data.data.content);
    } catch (error) {
      console.error('Failed to fetch jobs:', error);
    } finally {
      setLoading(false);
    }
  };

  const fetchIndex = async () => {
    try {
      const response = await api.get('/api/v1/search/indexing');
      setIndexTasks(response.data.data);
      setIndexError(false);
    } catch { setIndexError(true); }
  };

  const retryIndex = async (id: string) => {
    setRetryingIndex(id);
    try {
      await api.post(`/api/v1/search/indexing/JOB/${id}/retry`);
      await fetchIndex();
      toast({ title: 'Search preparation queued' });
    } catch { toast({ title: 'Could not retry search preparation', variant: 'destructive' }); }
    finally { setRetryingIndex(null); }
  };

  const analyzeJob = async () => {
    if (!jdText.trim() && !jobUrl.trim()) {
      toast({ title: 'Error', description: 'Please provide either a URL or the job description text.', variant: 'destructive' });
      return;
    }
    setAnalyzing(true);
    try {
      await api.post('/api/v1/jobs/analyze', {
        text: jdText.trim() || undefined,
        url: jobUrl.trim() || undefined,
        title: jobTitle || undefined
      });
      toast({ title: 'Job saved', description: 'An existing listing is reused when its source matches.' });
      fetchIndex();
      setOpen(false);
      setJdText('');
      setJobUrl('');
      setJobTitle('');
      fetchJobs();
    } catch (error) {
      toast({ title: 'Error', description: 'Failed to analyze job', variant: 'destructive' });
    } finally {
      setAnalyzing(false);
    }
  };

  const deleteJob = async (id: string) => {
    if (!confirm('Delete this job?')) return;
    try {
      await api.delete(`/api/v1/jobs/${id}`);
      fetchJobs();
    } catch (error) {
      toast({ title: 'Error', description: 'Failed to delete job', variant: 'destructive' });
    }
  };

  if (loading) return <div className="text-center py-8">Loading jobs...</div>;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-3xl font-bold tracking-tight">Job Descriptions</h2>
          <p className="text-muted-foreground">Save job descriptions for matching. Search preparation runs after saving.</p>
        </div>

        <Dialog open={open} onOpenChange={setOpen}>
          <DialogTrigger asChild>
            <Button><Zap className="mr-2 h-4 w-4" /> Analyze JD</Button>
          </DialogTrigger>
          <DialogContent className="sm:max-w-[600px]">
            <DialogHeader>
              <DialogTitle>Analyze Job Description</DialogTitle>
            </DialogHeader>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label>Job Title (Optional)</Label>
                <Input
                  placeholder="e.g. Senior Java Developer"
                  value={jobTitle}
                  onChange={(e) => setJobTitle(e.target.value)}
                />
              </div>

              <div className="space-y-2">
                <Label>Option 1: Job URL</Label>
                <Input
                  placeholder="Paste LinkedIn, Indeed, or any job URL..."
                  value={jobUrl}
                  onChange={(e) => setJobUrl(e.target.value)}
                />
              </div>

              <div className="relative py-2">
                <div className="absolute inset-0 flex items-center">
                  <span className="w-full border-t" />
                </div>
                <div className="relative flex justify-center text-xs uppercase">
                  <span className="bg-background px-2 text-muted-foreground">Or</span>
                </div>
              </div>

              <div className="space-y-2">
                <Label>Option 2: Paste Job Description</Label>
                <Textarea
                  placeholder="Paste the full job description here..."
                  className="min-h-[200px]"
                  value={jdText}
                  onChange={(e) => setJdText(e.target.value)}
                />
              </div>

              <Button onClick={analyzeJob} disabled={analyzing || (!jdText.trim() && !jobUrl.trim())} className="w-full">
                {analyzing ? 'Analyzing with AI...' : 'Analyze'}
              </Button>
            </div>
          </DialogContent>
        </Dialog>
      </div>

      {indexError && <p role="status" className="text-sm text-amber-700">Search preparation status is temporarily unavailable.</p>}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {jobs.map((job) => (
          <Card key={job.id} className="flex flex-col h-full hover:shadow-md transition-shadow">
            <CardHeader className="pb-3">
              <div className="flex justify-between items-start">
                <CardTitle className="text-lg line-clamp-2">{job.title}</CardTitle>
                <Button variant="ghost" size="icon" className="h-8 w-8 text-destructive -mr-2 -mt-2" onClick={() => deleteJob(job.id)}>
                  <Trash2 className="h-4 w-4" />
                </Button>
              </div>
              <div className="flex items-center text-sm text-muted-foreground mt-1 gap-4">
                <span className="flex items-center"><Building className="h-3 w-3 mr-1" /> {job.company || 'Unknown'}</span>
                <span className="flex items-center"><MapPin className="h-3 w-3 mr-1" /> {job.location || 'Any'}</span>
              </div>
              <div className="mt-2 flex items-center gap-2">
                <Badge variant="outline">{indexError ? 'Search status unavailable' : (() => {
                  const task = indexTasks.find(task => task.kind === 'JOB' && task.entity_id === job.id);
                  return task?.status === 'COMPLETED' ? 'Search prepared' : task?.status === 'FAILED' ? 'Search preparation failed' : task ? 'Preparing search' : 'Search status unavailable';
                })()}</Badge>
                {indexTasks.some(task => task.kind === 'JOB' && task.entity_id === job.id && task.status === 'FAILED') &&
                  <Button size="sm" variant="outline" disabled={retryingIndex !== null} onClick={() => retryIndex(job.id)}>Retry</Button>}
              </div>
            </CardHeader>
            <CardContent className="flex-1 flex flex-col justify-between">
              <div className="space-y-4">
                <div className="flex flex-wrap gap-2">
                  {job.experienceLevel && <Badge variant="secondary">{job.experienceLevel}</Badge>}
                  {job.jobType && <Badge variant="outline">{job.jobType}</Badge>}
                </div>

                {job.skillsRequired && job.skillsRequired.length > 0 && (
                  <div>
                    <p className="text-xs font-semibold uppercase text-muted-foreground mb-2">Required Skills</p>
                    <div className="flex flex-wrap gap-1">
                      {job.skillsRequired.slice(0, 5).map((skill, i) => (
                        <Badge key={i} variant="outline" className="text-xs bg-primary/5">{skill}</Badge>
                      ))}
                      {job.skillsRequired.length > 5 && (
                        <Badge variant="outline" className="text-xs">+{job.skillsRequired.length - 5} more</Badge>
                      )}
                    </div>
                  </div>
                )}
              </div>
              <div className="mt-6">
                <Button variant="outline" className="w-full" onClick={() => setSelectedJob(job)}>
                  <Eye className="mr-2 h-4 w-4" />
                  View Details
                </Button>
              </div>
            </CardContent>
          </Card>
        ))}
        {jobs.length === 0 && (
          <div className="col-span-full text-center py-12 border-2 border-dashed rounded-lg border-muted">
            <Briefcase className="mx-auto h-12 w-12 text-muted-foreground mb-4 opacity-50" />
            <h3 className="text-lg font-medium text-muted-foreground">No jobs analyzed</h3>
            <p className="text-sm text-muted-foreground mt-1 mb-4">Paste a job description to extract structured requirements.</p>
            <Button onClick={() => setOpen(true)} variant="outline">Analyze First Job</Button>
          </div>
        )}
      </div>

      {/* Job Details Dialog */}
      <Dialog open={!!selectedJob} onOpenChange={(open) => !open && setSelectedJob(null)}>
        <DialogContent className="sm:max-w-[800px] max-h-[90vh] overflow-y-auto">
          {selectedJob && (
            <>
              <DialogHeader>
                <div className="flex justify-between items-start pr-8">
                  <div>
                    <DialogTitle className="text-2xl font-bold">{selectedJob.title}</DialogTitle>
                    <div className="flex items-center gap-4 text-muted-foreground mt-2">
                      <span className="flex items-center"><Building className="h-4 w-4 mr-1" /> {selectedJob.company}</span>
                      <span className="flex items-center"><MapPin className="h-4 w-4 mr-1" /> {selectedJob.location}</span>
                    </div>
                  </div>
                  {selectedJob.sourceUrl && (
                    <Button variant="outline" size="sm" onClick={() => window.open(selectedJob.sourceUrl, '_blank')}>
                      <ExternalLink className="h-4 w-4 mr-2" /> Original Post
                    </Button>
                  )}
                </div>
              </DialogHeader>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-6 py-6 border-t border-b mt-6">
                <div className="space-y-1">
                  <p className="text-xs font-semibold text-muted-foreground uppercase flex items-center">
                    <DollarSign className="h-3 w-3 mr-1" /> Salary Range
                  </p>
                  <p className="font-medium">{selectedJob.salaryRange || 'Not specified'}</p>
                </div>
                <div className="space-y-1">
                  <p className="text-xs font-semibold text-muted-foreground uppercase flex items-center">
                    <Briefcase className="h-3 w-3 mr-1" /> Job Type
                  </p>
                  <p className="font-medium">{selectedJob.jobType || 'Full-time'}</p>
                </div>
                <div className="space-y-1">
                  <p className="text-xs font-semibold text-muted-foreground uppercase flex items-center">
                    <Calendar className="h-3 w-3 mr-1" /> Experience
                  </p>
                  <p className="font-medium">{selectedJob.experienceLevel || 'Mid-level'}</p>
                </div>
              </div>

              <div className="space-y-6 py-6">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                  <div>
                    <h4 className="font-bold mb-4 flex items-center">
                      <Zap className="h-4 w-4 mr-2 text-primary" /> Required Skills
                    </h4>
                    <div className="flex flex-wrap gap-2">
                      {selectedJob.skillsRequired?.map((skill, i) => (
                        <Badge key={i} variant="secondary" className="bg-primary/5 text-primary border-primary/10">
                          {skill}
                        </Badge>
                      ))}
                    </div>
                  </div>
                  {selectedJob.skillsNice && selectedJob.skillsNice.length > 0 && (
                    <div>
                      <h4 className="font-bold mb-4 flex items-center">
                        <Sparkles className="h-4 w-4 mr-2 text-secondary" /> Nice to Have
                      </h4>
                      <div className="flex flex-wrap gap-2">
                        {selectedJob.skillsNice.map((skill, i) => (
                          <Badge key={i} variant="outline" className="border-secondary/20 text-secondary-foreground">
                            {skill}
                          </Badge>
                        ))}
                      </div>
                    </div>
                  )}
                </div>

                <div className="space-y-4">
                  <h4 className="font-bold">Full Description</h4>
                  <div className="bg-muted/30 p-6 rounded-xl border text-sm leading-relaxed whitespace-pre-wrap max-h-[300px] overflow-y-auto">
                    {selectedJob?.rawText}
                  </div>
                </div>
              </div>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
