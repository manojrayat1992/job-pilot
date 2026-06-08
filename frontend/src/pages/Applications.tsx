import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import api from '@/lib/api';
import { JobApplication } from '@/types';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Plus,
  Search,
  Calendar,
  MapPin,
  Building2,
  MoreHorizontal,
  Clock,
  CheckCircle2,
  XCircle,
  AlertCircle,
  Trophy,
  RefreshCw,
  Mail
} from 'lucide-react';
import { useToast } from '@/hooks/use-toast';
import { LoadingSpinner } from '@/components/loading/LoadingSpinner';
import { useApiCall } from '@/hooks/useApiCall';

const STATUS_CONFIG = {
  SAVED: { title: 'Saved', color: 'bg-slate-100 text-slate-700 border-slate-200', icon: Clock },
  APPLIED: { title: 'Applied', color: 'bg-blue-100 text-blue-700 border-blue-200', icon: CheckCircle2 },
  PHONE_SCREEN: { title: 'Phone Screen', color: 'bg-purple-100 text-purple-700 border-purple-200', icon: AlertCircle },
  INTERVIEW: { title: 'Interviewing', color: 'bg-amber-100 text-amber-700 border-amber-200', icon: Calendar },
  OFFER: { title: 'Offer', color: 'bg-emerald-100 text-emerald-700 border-emerald-200', icon: Trophy },
  REJECTED: { title: 'Rejected', color: 'bg-rose-100 text-rose-700 border-rose-200', icon: XCircle },
};

export default function Applications() {
  const navigate = useNavigate();
  const [applications, setApplications] = useState<JobApplication[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [isSyncing, setIsSyncing] = useState(false);
  const { execute, isLoading } = useApiCall();
  const { toast } = useToast();

  useEffect(() => {
    fetchApplications();
  }, []);

  const fetchApplications = async () => {
    await execute(
      async () => {
        const res = await api.get('/api/v1/applications?size=50');
        setApplications(res.data.data.content);
      },
      { errorMessage: 'Failed to load applications. Please try again.' }
    );
  };

  const filteredApplications = applications.filter(app => 
    app.company.toLowerCase().includes(searchQuery.toLowerCase()) ||
    app.position.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const handleEmailSync = async () => {
    setIsSyncing(true);
    toast({ title: 'Sync Started', description: 'Scanning inbox for application updates...', duration: 2000 });
    try {
      const res = await api.post('/api/v1/integrations/email/sync');
      const updatedCount = res.data.data.updatedApplications;
      if (updatedCount > 0) {
        toast({ title: 'Inbox Synchronized', description: `Detected ${updatedCount} status update(s) from your emails.` });
        fetchApplications();
      } else {
        toast({ title: 'Inbox Synchronized', description: 'No new status updates found in your recent emails.' });
      }
    } catch (error) {
      toast({ title: 'Sync Failed', description: 'Could not connect to the email provider. Please ensure OAuth is configured.', variant: 'destructive' });
    } finally {
      setIsSyncing(false);
    }
  };

  if (isLoading && applications.length === 0) {
    return (
      <LoadingSpinner 
        size="lg" 
        message="Synchronizing your career journey..."
        className="min-h-[400px]"
      />
    );
  }

  return (
    <div className="space-y-8 pb-10">
      {/* Header Section */}
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-6">
        <div className="space-y-1">
          <h2 className="text-4xl font-extrabold tracking-tight">Application Tracker</h2>
          <p className="text-muted-foreground text-lg">
            Track your progress across <span className="text-primary font-bold">{applications.length}</span> active opportunities.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <div className="relative group">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground group-focus-within:text-primary transition-colors" />
            <input 
              type="text" 
              placeholder="Filter by company or role..."
              className="pl-10 pr-4 py-2 bg-background border rounded-lg text-sm w-64 focus:outline-none focus:ring-2 focus:ring-primary/20 transition-all"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>
          <Button variant="outline" onClick={handleEmailSync} disabled={isSyncing} className="shadow-sm border-indigo-200 text-indigo-700 bg-indigo-50 hover:bg-indigo-100">
            <Mail className="mr-2 h-4 w-4" />
            {isSyncing ? 'Scanning...' : 'Sync Inbox'}
            {isSyncing && <RefreshCw className="ml-2 h-4 w-4 animate-spin" />}
          </Button>
          <Button onClick={() => navigate('/applications/new')} className="shadow-lg shadow-primary/20 font-bold">
            <Plus className="mr-2 h-5 w-5" />
            Add Application
          </Button>
        </div>
      </div>

      {/* Kanban Board */}
      <div className="flex gap-6 overflow-x-auto pb-6 scrollbar-hide min-h-[600px]">
        {Object.entries(STATUS_CONFIG).map(([statusKey, config]) => {
          const colApps = filteredApplications.filter((a) => a.status === statusKey);
          const Icon = config.icon;

          return (
            <div key={statusKey} className="flex-shrink-0 w-80 space-y-4">
              <div className="flex items-center justify-between px-2">
                <div className="flex items-center gap-2">
                  <div className={`p-1.5 rounded-md ${config.color.split(' ')[0]} ${config.color.split(' ')[1]}`}>
                    <Icon className="h-4 w-4" />
                  </div>
                  <h3 className="font-bold text-slate-800">{config.title}</h3>
                </div>
                <Badge variant="outline" className="font-bold bg-white shadow-sm border-slate-200">
                  {colApps.length}
                </Badge>
              </div>
              
              <div className="space-y-4 min-h-[500px] rounded-xl bg-slate-50/50 p-3 border-2 border-dashed border-slate-200/50">
                {colApps.length === 0 ? (
                  <div className="h-32 flex flex-col items-center justify-center text-center p-4 opacity-40">
                    <div className="h-10 w-10 border-2 border-dashed border-slate-400 rounded-full mb-2" />
                    <p className="text-xs font-medium text-slate-500">No applications in this stage</p>
                  </div>
                ) : (
                  colApps.map((app) => (
                    <Card 
                      key={app.id} 
                      className="group cursor-pointer hover:shadow-xl hover:-translate-y-1 transition-all duration-300 border-none ring-1 ring-slate-200 shadow-sm overflow-hidden"
                      onClick={() => navigate(`/applications/${app.id}`)}
                    >
                      <CardHeader className="p-4 pb-2 space-y-3">
                        <div className="flex justify-between items-start">
                          <div className="h-10 w-10 bg-slate-100 rounded-lg flex items-center justify-center text-slate-400 group-hover:bg-primary/10 group-hover:text-primary transition-colors">
                            <Building2 className="h-6 w-6" />
                          </div>
                          <button className="text-slate-300 hover:text-slate-600 transition-colors">
                            <MoreHorizontal className="h-5 w-5" />
                          </button>
                        </div>
                        <div className="space-y-1">
                          <CardTitle className="text-base font-bold group-hover:text-primary transition-colors line-clamp-1">
                            {app.position}
                          </CardTitle>
                          <p className="text-sm text-slate-600 font-semibold flex items-center gap-1">
                            {app.company}
                          </p>
                        </div>
                      </CardHeader>
                      <CardContent className="p-4 pt-2 space-y-4">
                        <div className="flex flex-wrap gap-2">
                          {app.priority && (
                            <Badge variant="secondary" className="text-[10px] uppercase font-black tracking-wider bg-orange-50 text-orange-600 border-orange-100">
                              {app.priority} Priority
                            </Badge>
                          )}
                          {app.source && (
                            <Badge variant="outline" className="text-[10px] font-medium border-slate-200 text-slate-500">
                              via {app.source}
                            </Badge>
                          )}
                        </div>
                        
                        <div className="flex items-center justify-between pt-2 border-t border-slate-100">
                          <div className="flex items-center text-[11px] text-slate-400 font-medium">
                            <Calendar className="h-3 w-3 mr-1" />
                            {app.appliedDate ? new Date(app.appliedDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }) : 'Date TBD'}
                          </div>
                          <div className="flex items-center text-[11px] text-slate-400 font-medium">
                            <MapPin className="h-3 w-3 mr-1" />
                            Remote
                          </div>
                        </div>
                      </CardContent>
                    </Card>
                  ))
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
