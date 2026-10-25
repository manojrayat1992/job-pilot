import { Button } from '@/components/ui/button';
import { Card,CardContent,CardDescription,CardHeader,CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { useToast } from '@/hooks/use-toast';
import api from '@/lib/api';
import { Job,Resume } from '@/types';
import { ArrowLeft,Briefcase,Building,FileText,Link2,Loader2,Save } from 'lucide-react';
import { useEffect,useState } from 'react';
import { useNavigate } from 'react-router-dom';

export default function NewApplication() {
  const navigate = useNavigate();
  const { toast } = useToast();
  
  const [resumes, setResumes] = useState<Resume[]>([]);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  
  const [formData, setFormData] = useState({
    company: '',
    position: '',
    status: 'SAVED',
    appliedDate: new Date().toISOString().split('T')[0],
    notes: '',
    source: '',
    jobId: '',
    resumeId: '',
    contactName: '',
    contactEmail: ''
  });

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
      console.error('Failed to fetch data:', error);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.company || !formData.position) {
      toast({ title: 'Error', description: 'Company and Position are required.', variant: 'destructive' });
      return;
    }

    setIsLoading(true);
    try {
      await api.post('/api/v1/applications', {
        ...formData,
        jobId: formData.jobId || undefined,
        resumeId: formData.resumeId || undefined
      });
      toast({ title: 'Success', description: 'Application added to tracker!' });
      navigate('/applications');
    } catch (error) {
      toast({ title: 'Error', description: 'Failed to save application.', variant: 'destructive' });
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-muted/30 pb-20">
      <div className="bg-background border-b sticky top-0 z-10">
        <div className="container mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Button variant="ghost" size="icon" onClick={() => navigate(-1)}>
              <ArrowLeft className="h-5 w-5" />
            </Button>
            <h1 className="text-xl font-bold">Add New Application</h1>
          </div>
          <Button onClick={handleSubmit} disabled={isLoading}>
            {isLoading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
            Save Application
          </Button>
        </div>
      </div>

      <div className="container mx-auto px-4 py-8 max-w-4xl">
        <form onSubmit={handleSubmit} className="space-y-8">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
            {/* Core Details */}
            <Card className="md:col-span-2">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Building className="h-5 w-5 text-primary" />
                  Job Information
                </CardTitle>
                <CardDescription>Enter the basic details of the position.</CardDescription>
              </CardHeader>
              <CardContent className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="company">Company Name *</Label>
                  <Input 
                    id="company" 
                    placeholder="e.g. Google" 
                    value={formData.company}
                    onChange={e => setFormData({...formData, company: e.target.value})}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="position">Position Title *</Label>
                  <Input 
                    id="position" 
                    placeholder="e.g. Senior Software Engineer" 
                    value={formData.position}
                    onChange={e => setFormData({...formData, position: e.target.value})}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="status">Application Status</Label>
                  <select 
                    id="status"
                    className="w-full p-2 rounded-md border bg-background text-sm"
                    value={formData.status}
                    onChange={e => setFormData({...formData, status: e.target.value})}
                  >
                    <option value="SAVED">Saved / Draft</option>
                    <option value="APPLIED">Applied</option>
                    <option value="PHONE_SCREEN">Phone Screen</option>
                    <option value="INTERVIEW">Interviewing</option>
                    <option value="OFFER">Offer Received</option>
                    <option value="REJECTED">Rejected</option>
                  </select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="date">Applied Date</Label>
                  <Input 
                    id="date" 
                    type="date" 
                    value={formData.appliedDate}
                    onChange={e => setFormData({...formData, appliedDate: e.target.value})}
                  />
                </div>
              </CardContent>
            </Card>

            {/* AI Integration */}
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-lg">
                  <Link2 className="h-5 w-5 text-primary" />
                  Copilot Integration
                </CardTitle>
                <CardDescription>Link to existing data for better AI insights.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <Label>Source Job Analysis</Label>
                  <select 
                    className="w-full p-2 rounded-md border bg-background text-sm"
                    value={formData.jobId}
                    onChange={e => setFormData({...formData, jobId: e.target.value})}
                  >
                    <option value="">None (Standalone)</option>
                    {jobs.map(j => (
                      <option key={j.id} value={j.id}>{j.company} - {j.title}</option>
                    ))}
                  </select>
                </div>
                <div className="space-y-2">
                  <Label>Resume Used</Label>
                  <select 
                    className="w-full p-2 rounded-md border bg-background text-sm"
                    value={formData.resumeId}
                    onChange={e => setFormData({...formData, resumeId: e.target.value})}
                  >
                    <option value="">None selected</option>
                    {resumes.map(r => (
                      <option key={r.id} value={r.id}>{r.title} (v{r.version})</option>
                    ))}
                  </select>
                </div>
              </CardContent>
            </Card>

            {/* Contact Details */}
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-lg">
                  <Briefcase className="h-5 w-5 text-primary" />
                  Contact Details
                </CardTitle>
                <CardDescription>Keep track of the hiring manager or recruiter.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="contactName">Contact Name</Label>
                  <Input 
                    id="contactName" 
                    placeholder="e.g. Jane Doe" 
                    value={formData.contactName}
                    onChange={e => setFormData({...formData, contactName: e.target.value})}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="contactEmail">Contact Email</Label>
                  <Input 
                    id="contactEmail" 
                    type="email" 
                    placeholder="jane@company.com" 
                    value={formData.contactEmail}
                    onChange={e => setFormData({...formData, contactEmail: e.target.value})}
                  />
                </div>
              </CardContent>
            </Card>

            {/* Additional Info */}
            <Card className="md:col-span-2">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <FileText className="h-5 w-5 text-primary" />
                  Notes & Source
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="source">Source URL / Platform</Label>
                  <Input 
                    id="source" 
                    placeholder="e.g. LinkedIn, Referral, Company Careers Page" 
                    value={formData.source}
                    onChange={e => setFormData({...formData, source: e.target.value})}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="notes">Notes</Label>
                  <Textarea 
                    id="notes" 
                    placeholder="Add any specific details about the interview process, company culture, or follow-ups..." 
                    className="min-h-[120px]"
                    value={formData.notes}
                    onChange={e => setFormData({...formData, notes: e.target.value})}
                  />
                </div>
              </CardContent>
            </Card>
          </div>
        </form>
      </div>
    </div>
  );
}
