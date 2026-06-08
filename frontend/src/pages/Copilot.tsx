import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '@/lib/api';
import { Resume } from '@/types';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Label } from '@/components/ui/label';
import {
  ArrowLeft, Zap, ShieldCheck, 
  Download, ExternalLink, MousePointer2, Sparkles, 
  Bot
} from 'lucide-react';
import { useToast } from '@/hooks/use-toast';

export default function Copilot() {
  const navigate = useNavigate();
  const { toast } = useToast();
  const [resumes, setResumes] = useState<Resume[]>([]);
  const [selectedResumeId, setSelectedResumeId] = useState('');
  const [isExtensionDetected, setIsExtensionDetected] = useState(false);

  useEffect(() => {
    fetchResumes();
    // Check for extension injected attribute on document.documentElement
    const checkExtension = () => {
      if (document.documentElement.getAttribute('data-jobc-copilot-active') === 'true') {
        setIsExtensionDetected(true);
      }
    };
    checkExtension();
    const interval = setInterval(checkExtension, 2000);
    return () => clearInterval(interval);
  }, []);

  const fetchResumes = async () => {
    try {
      const res = await api.get('/api/v1/resumes');
      setResumes(res.data.data.content);
      const active = res.data.data.content.find((r: Resume) => r.active);
      if (active) setSelectedResumeId(active.id);
    } catch (error) {
      toast({ title: 'Error', description: 'Failed to fetch resumes.', variant: 'destructive' });
    }
  };

  const handleSetExtensionResume = () => {
    if (!selectedResumeId) return;
    // In a real app, we'd send this to the extension via window.postMessage
    window.postMessage({ type: 'SET_COPILOT_RESUME', resumeId: selectedResumeId }, '*');
    toast({ 
      title: 'Context Updated', 
      description: 'Your extension will now use this resume for Magic Fill.' 
    });
  };

  return (
    <div className="min-h-screen bg-slate-50 pb-20">
      <div className="fixed inset-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-indigo-100/50 via-slate-50 to-white -z-10" />

      <div className="bg-white/80 backdrop-blur-xl border-b border-slate-200 sticky top-0 z-20">
        <div className="container mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Button variant="ghost" size="icon" onClick={() => navigate('/dashboard')} className="hover:bg-slate-100 rounded-full">
              <ArrowLeft className="h-5 w-5 text-slate-700" />
            </Button>
            <div className="flex items-center gap-3">
              <div className="h-10 w-10 bg-indigo-600 rounded-xl flex items-center justify-center shadow-lg shadow-indigo-200">
                <Zap className="h-5 w-5 text-white" />
              </div>
              <div>
                <h1 className="text-xl font-bold text-slate-900 tracking-tight">One-Click Copilot</h1>
                <p className="text-xs font-medium text-indigo-600">v1.0 · Intelligent Extension Assist</p>
              </div>
            </div>
          </div>
          
          {isExtensionDetected ? (
             <Badge className="bg-emerald-50 text-emerald-700 border-emerald-100 px-4 py-2 rounded-full flex items-center gap-2">
                <div className="h-2 w-2 bg-emerald-500 rounded-full animate-pulse" />
                EXTENSION ACTIVE
             </Badge>
          ) : (
             <Badge variant="outline" className="text-slate-400 border-slate-200 px-4 py-2 rounded-full">
                EXTENSION NOT DETECTED
             </Badge>
          )}
        </div>
      </div>

      <div className="container mx-auto px-4 py-12 max-w-6xl">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 items-center">
          <div className="space-y-8">
            <div className="space-y-4">
              <h2 className="text-4xl font-black text-slate-900 leading-tight">
                Stop Applying. <br/>
                <span className="text-transparent bg-clip-text bg-gradient-to-r from-indigo-600 to-violet-600">Start Navigating.</span>
              </h2>
              <p className="text-lg text-slate-500 font-medium leading-relaxed">
                The One-Click Copilot lives in your browser. It semantically reads job applications and prepares your answers in real-time, allowing you to apply with 100% human-verified precision.
              </p>
            </div>

            <div className="grid grid-cols-1 gap-4">
              {[
                { title: 'Safe & Compliant', desc: 'Runs as you, in your browser. Zero risk of account bans.', icon: ShieldCheck, color: 'text-emerald-600' },
                { title: 'Magic Fill', desc: 'AI semantically maps your resume to complex form fields.', icon: MousePointer2, color: 'text-indigo-600' },
                { title: 'Instant Tailoring', desc: 'Generates custom cover letters directly on the job page.', icon: Sparkles, color: 'text-amber-600' },
              ].map((feature, i) => (
                <div key={i} className="flex items-start gap-4 p-4 bg-white rounded-3xl shadow-sm border border-slate-100">
                  <div className={`p-3 rounded-2xl bg-slate-50 ${feature.color}`}>
                    <feature.icon className="h-5 w-5" />
                  </div>
                  <div>
                    <h4 className="font-bold text-slate-900">{feature.title}</h4>
                    <p className="text-sm text-slate-500">{feature.desc}</p>
                  </div>
                </div>
              ))}
            </div>

            <div className="pt-4 flex flex-wrap gap-4">
               <Button className="h-14 px-8 rounded-2xl bg-slate-900 text-white font-bold hover:bg-slate-800 transition-all shadow-xl shadow-slate-200">
                  <Download className="mr-2 h-5 w-5" /> Download Extension
               </Button>
               <Button variant="outline" className="h-14 px-8 rounded-2xl font-bold border-slate-200">
                  <ExternalLink className="mr-2 h-5 w-5" /> Extension Guide
               </Button>
            </div>
          </div>

          <div className="space-y-6">
            <Card className="border-0 shadow-2xl shadow-indigo-100 rounded-[2.5rem] overflow-hidden bg-white">
              <div className="h-3 w-full bg-indigo-600" />
              <CardHeader className="pb-4">
                <CardTitle className="text-2xl font-black">Copilot Context</CardTitle>
                <CardDescription className="font-medium">
                  Select the resume you want the extension to use for "Magic Fill" operations.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-6">
                <div className="space-y-3">
                  <Label className="text-slate-700 font-bold">Default Resume Profile</Label>
                  <select
                    className="w-full h-14 rounded-2xl border bg-white px-4 text-sm outline-none focus:ring-2 focus:ring-indigo-500/20 shadow-sm"
                    value={selectedResumeId}
                    onChange={(e) => setSelectedResumeId(e.target.value)}
                  >
                    <option value="">Select a profile...</option>
                    {resumes.map(r => (
                      <option key={r.id} value={r.id}>{r.title} {r.active ? '(Active)' : ''}</option>
                    ))}
                  </select>
                </div>

                <Button 
                  onClick={handleSetExtensionResume}
                  disabled={!selectedResumeId}
                  className="w-full h-14 rounded-2xl bg-indigo-600 hover:bg-indigo-700 text-white font-black text-lg shadow-lg shadow-indigo-100 transition-all"
                >
                  Sync to Extension
                </Button>

                <div className="p-6 bg-slate-50 rounded-[2rem] border border-slate-100 space-y-4">
                   <div className="flex items-center gap-2 text-slate-800 font-bold">
                      <Bot className="h-5 w-5 text-indigo-600" />
                      <span>How to use Magic Fill:</span>
                   </div>
                   <ol className="space-y-3">
                      {[
                        'Navigate to any LinkedIn or Workday job post.',
                        'The Copilot sidebar will automatically slide out.',
                        'Click "Magic Fill" to pre-populate the form.',
                        'Verify the data and click Submit manually.'
                      ].map((step, i) => (
                        <li key={i} className="flex gap-3 text-sm text-slate-500 font-medium leading-tight">
                           <span className="h-5 w-5 rounded-full bg-white border border-slate-200 flex-shrink-0 flex items-center justify-center text-[10px] font-black">{i+1}</span>
                           {step}
                        </li>
                      ))}
                   </ol>
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      </div>
    </div>
  );
}
