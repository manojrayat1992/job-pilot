import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card,CardContent } from '@/components/ui/card';
import { useToast } from '@/hooks/use-toast';
import api from '@/lib/api';
import { JobApplication } from '@/types';
import {
ArrowLeft,
Building2,
Calendar,
ChevronRight,
Clock,
Edit3,
FileText,
Mail,
MapPin,
MessageSquare,
ShieldCheck,
Sparkles,
Target,
Trash2,
User
} from 'lucide-react';
import { useEffect,useState } from 'react';
import { useNavigate,useParams } from 'react-router-dom';

const STAGES = [
  { id: 'SAVED', label: 'Draft' },
  { id: 'APPLIED', label: 'Applied' },
  { id: 'PHONE_SCREEN', label: 'Screening' },
  { id: 'INTERVIEW', label: 'Interview' },
  { id: 'OFFER', label: 'Offer' }
];

export default function ApplicationDetails() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { toast } = useToast();
  
  const [application, setApplication] = useState<JobApplication | null>(null);
  const [loading, setLoading] = useState(true);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    fetchDetails();
  }, [id]);

  const fetchDetails = async () => {
    try {
      const res = await api.get(`/api/v1/applications/${id}`);
      setApplication(res.data.data);
    } catch (error) {
      toast({ title: 'Error', description: 'Opportunity not found.', variant: 'destructive' });
      navigate('/applications');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!confirm('This will permanently remove this opportunity from your tracker. Proceed?')) return;
    
    setDeleting(true);
    try {
      await api.delete(`/api/v1/applications/${id}`);
      toast({ title: 'Success', description: 'Application purged successfully.' });
      navigate('/applications');
    } catch (error) {
      toast({ title: 'Error', description: 'Failed to delete application.', variant: 'destructive' });
    } finally {
      setDeleting(false);
    }
  };

  const currentStageIndex = STAGES.findIndex(s => s.id === application?.status);

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] space-y-4">
        <div className="relative">
            <div className="h-16 w-16 border-4 border-primary/20 border-t-primary rounded-full animate-spin" />
            <Sparkles className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 h-6 w-6 text-primary animate-pulse" />
        </div>
        <p className="text-slate-500 font-medium animate-pulse tracking-wide">Orchestrating Details...</p>
      </div>
    );
  }

  if (!application) return null;

  return (
    <div className="min-h-screen pb-24 relative overflow-hidden">
      {/* Premium Background Elements */}
      <div className="absolute top-0 left-0 w-full h-[500px] bg-gradient-to-b from-primary/5 to-transparent -z-10" />
      <div className="absolute -top-24 -right-24 w-96 h-96 bg-primary/10 rounded-full blur-3xl -z-10 opacity-50" />
      
      <div className="max-w-6xl mx-auto px-4 pt-8 space-y-8">
        
        {/* Navigation Bar */}
        <div className="flex items-center justify-between backdrop-blur-sm bg-white/50 p-2 rounded-2xl border border-white/50 shadow-sm sticky top-4 z-50">
          <Button variant="ghost" onClick={() => navigate('/applications')} className="rounded-xl hover:bg-white group">
            <ArrowLeft className="h-4 w-4 mr-2 group-hover:-translate-x-1 transition-transform" /> 
            Back to Tracker
          </Button>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" className="rounded-xl bg-white border-slate-200">
              <Edit3 className="h-4 w-4 mr-2" /> Edit Details
            </Button>
            <Button variant="ghost" size="sm" className="rounded-xl text-rose-500 hover:bg-rose-50 hover:text-rose-600" onClick={handleDelete} disabled={deleting}>
              <Trash2 className="h-4 w-4 mr-2" /> {deleting ? 'Purging...' : 'Remove'}
            </Button>
          </div>
        </div>

        {/* Hero Header Section */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          <div className="lg:col-span-8 space-y-8">
            
            {/* Header Content */}
            <div className="space-y-6">
              <div className="flex flex-wrap items-center gap-3">
                <Badge className="bg-slate-900 text-white hover:bg-slate-900 px-4 py-1 rounded-full text-[10px] font-bold tracking-widest uppercase">
                  {application.company}
                </Badge>
                <div className="h-1 w-1 bg-slate-300 rounded-full" />
                <span className="text-slate-400 text-sm font-medium flex items-center gap-1">
                  <MapPin className="h-3 w-3" /> Remote Opportunity
                </span>
              </div>
              <h1 className="text-5xl font-black tracking-tight text-slate-900 leading-[1.1]">
                {application.position}
              </h1>
            </div>

            {/* Hiring Roadmap */}
            <Card className="bg-white/80 backdrop-blur-md border-white/50 shadow-xl shadow-slate-200/50 rounded-3xl overflow-hidden">
                <CardContent className="p-8 space-y-8">
                  <div className="flex items-center justify-between mb-4">
                    <h3 className="text-sm font-black uppercase tracking-widest text-slate-400 flex items-center gap-2">
                      <Target className="h-4 w-4" /> Hiring Journey
                    </h3>
                    <Badge variant="secondary" className="bg-primary/10 text-primary border-none font-bold">
                      Current Stage: {STAGES[currentStageIndex]?.label || 'Active'}
                    </Badge>
                  </div>
                  
                  <div className="relative">
                    <div className="absolute top-1/2 left-0 w-full h-1 bg-slate-100 -translate-y-1/2 rounded-full" />
                    <div 
                      className="absolute top-1/2 left-0 h-1 bg-primary -translate-y-1/2 rounded-full transition-all duration-1000 ease-out" 
                      style={{ width: `${(currentStageIndex / (STAGES.length - 1)) * 100}%` }}
                    />
                    
                    <div className="flex justify-between relative">
                      {STAGES.map((stage, i) => {
                        const isCompleted = i < currentStageIndex;
                        const isCurrent = i === currentStageIndex;
                        return (
                          <div key={stage.id} className="flex flex-col items-center gap-3">
                            <div className={`h-10 w-10 rounded-full border-4 flex items-center justify-center transition-all duration-500 z-10 ${
                              isCompleted ? 'bg-primary border-primary text-white shadow-lg shadow-primary/30' : 
                              isCurrent ? 'bg-white border-primary text-primary scale-125 shadow-xl ring-8 ring-primary/5' : 
                              'bg-white border-slate-100 text-slate-300'
                            }`}>
                              {isCompleted ? <ShieldCheck className="h-5 w-5" /> : <span className="text-xs font-bold">{i + 1}</span>}
                            </div>
                            <span className={`text-[10px] font-black uppercase tracking-wider transition-colors ${isCurrent ? 'text-primary' : 'text-slate-400'}`}>
                              {stage.label}
                            </span>
                          </div>
                        )
                      })}
                    </div>
                  </div>
                </CardContent>
            </Card>

            {/* Notes & Insights */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <Card className="rounded-3xl border-none shadow-lg bg-white overflow-hidden">
                  <div className="h-2 w-full bg-primary" />
                  <CardContent className="p-8 space-y-4">
                    <h3 className="font-black text-slate-900 flex items-center gap-2 italic">
                      <MessageSquare className="h-5 w-5 text-primary" /> Strategist Notes
                    </h3>
                    <p className="text-slate-600 text-sm leading-relaxed min-h-[100px]">
                      {application.notes || "No strategic notes provided for this target."}
                    </p>
                  </CardContent>
                </Card>

                <Card className="rounded-3xl border-none shadow-lg bg-slate-900 text-white group cursor-pointer hover:bg-slate-800 transition-colors">
                  <CardContent className="p-8 h-full flex flex-col justify-between space-y-6">
                    <div className="space-y-4">
                      <div className="h-12 w-12 rounded-2xl bg-primary flex items-center justify-center shadow-lg shadow-primary/20">
                        <Sparkles className="h-6 w-6 text-white" />
                      </div>
                      <h3 className="text-xl font-bold">AI Interview Prep</h3>
                      <p className="text-slate-400 text-sm leading-relaxed">
                        Generate tailored interview answers based on your resume and this job description.
                      </p>
                    </div>
                    <Button variant="ghost" className="w-full justify-between text-primary font-bold p-0 hover:bg-transparent">
                      Deploy Coach <ChevronRight className="h-4 w-4 group-hover:translate-x-2 transition-transform" />
                    </Button>
                  </CardContent>
                </Card>
            </div>
          </div>

          {/* Sidebar Area */}
          <div className="lg:col-span-4 space-y-8">
            
            {/* Quick Stats Card */}
            <Card className="rounded-3xl border-none shadow-xl bg-white p-2">
               <CardContent className="p-6 space-y-6">
                  <h3 className="text-xs font-black uppercase tracking-widest text-slate-400 px-2">Metadata</h3>
                  <div className="space-y-1">
                    <div className="flex items-center justify-between p-3 hover:bg-slate-50 rounded-xl transition-colors">
                      <div className="flex items-center gap-3">
                        <Calendar className="h-4 w-4 text-slate-400" />
                        <span className="text-xs font-bold text-slate-500 uppercase tracking-tighter">Applied</span>
                      </div>
                      <span className="text-sm font-bold text-slate-700">
                        {application.appliedDate ? new Date(application.appliedDate).toLocaleDateString() : 'Pending'}
                      </span>
                    </div>
                    <div className="flex items-center justify-between p-3 hover:bg-slate-50 rounded-xl transition-colors">
                      <div className="flex items-center gap-3">
                        <Clock className="h-4 w-4 text-slate-400" />
                        <span className="text-xs font-bold text-slate-500 uppercase tracking-tighter">Tracking Since</span>
                      </div>
                      <span className="text-sm font-bold text-slate-700">
                        {new Date(application.createdAt).toLocaleDateString()}
                      </span>
                    </div>
                  </div>
               </CardContent>
            </Card>

            {/* Recruiter / Contact */}
            <Card className="rounded-3xl border-none shadow-xl bg-white p-2 overflow-hidden">
               <div className="p-8 space-y-6">
                  <h3 className="text-xs font-black uppercase tracking-widest text-slate-400">Point of Contact</h3>
                  {application.contactName || application.contactEmail ? (
                    <div className="space-y-6">
                      <div className="flex items-center gap-4">
                        <div className="h-12 w-12 bg-slate-100 rounded-full flex items-center justify-center text-slate-400">
                          <User className="h-6 w-6" />
                        </div>
                        <div>
                          <p className="text-lg font-black text-slate-900 leading-none">{application.contactName || "HR Department"}</p>
                          <p className="text-sm text-slate-500 font-medium mt-1">{application.contactEmail || "Contact not linked"}</p>
                        </div>
                      </div>
                      {application.contactEmail && (
                        <Button className="w-full rounded-2xl gap-2 font-bold bg-white text-slate-900 border-2 border-slate-100 hover:bg-slate-50 shadow-none">
                          <Mail className="h-4 w-4" /> Reach Out
                        </Button>
                      )}
                    </div>
                  ) : (
                    <p className="text-sm text-slate-400 italic">No contact details stored.</p>
                  )}
               </div>
            </Card>

            {/* Linked Assets */}
            <div className="space-y-4">
              <h3 className="text-[10px] font-black uppercase tracking-[0.2em] text-slate-400 px-6">Linked Intelligence</h3>
              <div className="grid grid-cols-2 gap-4">
                 <button className={`p-4 rounded-3xl flex flex-col items-center justify-center gap-3 transition-all ${
                   application.jobId ? 'bg-indigo-50 text-indigo-600 hover:bg-indigo-100' : 'bg-slate-50 text-slate-300 opacity-50 cursor-not-allowed'
                 }`}>
                    <Building2 className="h-6 w-6" />
                    <span className="text-[10px] font-black uppercase">Job Bio</span>
                 </button>
                 <button className={`p-4 rounded-3xl flex flex-col items-center justify-center gap-3 transition-all ${
                   application.resumeId ? 'bg-emerald-50 text-emerald-600 hover:bg-emerald-100' : 'bg-slate-50 text-slate-300 opacity-50 cursor-not-allowed'
                 }`}>
                    <FileText className="h-6 w-6" />
                    <span className="text-[10px] font-black uppercase">Resume</span>
                 </button>
              </div>
            </div>

          </div>
        </div>

      </div>
    </div>
  );
}
