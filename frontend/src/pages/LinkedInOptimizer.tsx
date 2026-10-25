import { Button } from '@/components/ui/button';
import { useToast } from '@/hooks/use-toast';
import api from '@/lib/api';
import { motion } from 'framer-motion';
import {
AlertCircle,
ArrowLeft,
CheckCircle2,
Copy,
Linkedin,
Loader2,Sparkles,Target
} from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

export default function LinkedInOptimizer() {
  const [form, setForm] = useState({ headline: '', summary: '', experience: '', targetRole: '' });
  const [analyzing, setAnalyzing] = useState(false);
  const [result, setResult] = useState<any>(null);
  const { toast } = useToast();
  const navigate = useNavigate();

  const handleOptimize = async () => {
    if (!form.targetRole) {
      toast({ title: 'Target Role required', description: 'Please enter your target job role.', variant: 'destructive' });
      return;
    }
    setAnalyzing(true);
    try {
      const res = await api.post('/api/v1/ai/linkedin/optimize', form);
      setResult(res.data.data);
    } catch (error: any) {
      if (error.response?.status === 402) {
        toast({ title: 'Credits Exhausted', description: 'Paid upgrades are currently unavailable.', variant: 'destructive' });
      } else {
        toast({ title: 'Optimization Failed', description: 'Please try again.', variant: 'destructive' });
      }
    } finally {
      setAnalyzing(false);
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    toast({ title: 'Copied to clipboard' });
  };

  return (
    <div className="min-h-screen bg-slate-50/50 pb-20 relative overflow-hidden">
      <div className="absolute top-0 left-0 w-full h-[500px] bg-gradient-to-b from-blue-50 via-white to-transparent -z-10" />
      <div className="absolute top-[-20%] right-[-10%] w-[50%] h-[500px] bg-blue-200/40 rounded-full blur-[120px] -z-10 mix-blend-multiply" />

      <div className="max-w-6xl mx-auto space-y-8 px-4 pt-6">
        <div className="flex items-center justify-between">
          <Button variant="ghost" onClick={() => navigate(-1)} className="gap-2 text-slate-600 hover:text-slate-900">
            <ArrowLeft className="h-4 w-4" /> Back
          </Button>
          <div className="flex items-center gap-2 text-sm font-bold text-blue-700 bg-white/80 backdrop-blur-md px-5 py-2.5 rounded-full border border-blue-100 shadow-sm">
            <Linkedin className="h-4 w-4 text-[#0A66C2]" />
            <span>LINKEDIN OPTIMIZER</span>
          </div>
        </div>

        {!result ? (
          <div className="flex flex-col items-center space-y-12 py-8">
            <div className="text-center space-y-4 max-w-2xl">
              <h1 className="text-5xl font-black tracking-tight text-slate-900">
                Attract More <span className="text-[#0A66C2]">Recruiters</span>
              </h1>
              <p className="text-lg text-slate-600 leading-relaxed">
                Paste your current LinkedIn content below. Our AI will rewrite it with ATS-friendly keywords to boost your search visibility for your target role.
              </p>
            </div>

            <div className="w-full max-w-3xl bg-white rounded-3xl shadow-xl border border-slate-100 p-8 space-y-6">
              <div className="space-y-2">
                <label className="text-sm font-bold text-slate-700">Target Role *</label>
                <input type="text" value={form.targetRole} onChange={e => setForm({ ...form, targetRole: e.target.value })}
                  placeholder="e.g. Senior Frontend Developer"
                  className="w-full px-4 py-3 rounded-xl border border-slate-200 focus:ring-2 focus:ring-[#0A66C2] focus:border-[#0A66C2] outline-none" />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-bold text-slate-700">Current Headline (Optional)</label>
                <input type="text" value={form.headline} onChange={e => setForm({ ...form, headline: e.target.value })}
                  placeholder="e.g. Software Engineer at Tech Corp"
                  className="w-full px-4 py-3 rounded-xl border border-slate-200 focus:ring-2 focus:ring-[#0A66C2] focus:border-[#0A66C2] outline-none" />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-bold text-slate-700">Current Summary / About (Optional)</label>
                <textarea value={form.summary} onChange={e => setForm({ ...form, summary: e.target.value })}
                  placeholder="Paste your current About section here..." rows={4}
                  className="w-full px-4 py-3 rounded-xl border border-slate-200 focus:ring-2 focus:ring-[#0A66C2] focus:border-[#0A66C2] outline-none resize-none" />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-bold text-slate-700">Experience Snippet (Optional)</label>
                <textarea value={form.experience} onChange={e => setForm({ ...form, experience: e.target.value })}
                  placeholder="Paste one of your job experience descriptions..." rows={4}
                  className="w-full px-4 py-3 rounded-xl border border-slate-200 focus:ring-2 focus:ring-[#0A66C2] focus:border-[#0A66C2] outline-none resize-none" />
              </div>
              
              <Button onClick={handleOptimize} disabled={analyzing}
                className="w-full h-14 rounded-2xl bg-[#0A66C2] hover:bg-[#004182] text-white font-bold text-lg gap-3 shadow-lg hover:shadow-xl transition-all">
                {analyzing ? <><Loader2 className="h-5 w-5 animate-spin" /> Optimizing Profile...</> : <><Sparkles className="h-5 w-5" /> Generate Optimized Profile</>}
              </Button>
            </div>
          </div>
        ) : (
          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="space-y-8 max-w-4xl mx-auto">
            <div className="flex items-center justify-between">
              <h2 className="text-3xl font-black text-slate-900">Optimized Profile</h2>
              <Button onClick={() => setResult(null)} variant="outline" className="rounded-full">Start Over</Button>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm flex items-center justify-between">
                <div>
                  <p className="text-sm text-slate-500 font-medium">Recruiter Visibility</p>
                  <p className="text-3xl font-black text-[#0A66C2]">{result.recruiterVisibilityScore}/100</p>
                </div>
                <Target className="h-10 w-10 text-blue-100" />
              </div>
              <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm flex items-center justify-between">
                <div>
                  <p className="text-sm text-slate-500 font-medium">Keyword Density</p>
                  <p className="text-3xl font-black text-emerald-600">{result.keywordDensityScore}/100</p>
                </div>
                <Sparkles className="h-10 w-10 text-emerald-100" />
              </div>
            </div>

            {/* Headline */}
            <div className="bg-white rounded-3xl border border-slate-200 shadow-sm overflow-hidden">
              <div className="bg-slate-50 px-6 py-4 border-b border-slate-200 flex justify-between items-center">
                <h3 className="font-bold text-slate-800 flex items-center gap-2"><Linkedin className="h-4 w-4" /> Optimized Headline</h3>
                <Button variant="ghost" size="sm" onClick={() => copyToClipboard(result.optimizedHeadline)}>
                  <Copy className="h-4 w-4" />
                </Button>
              </div>
              <div className="p-6">
                <p className="text-xl text-slate-900 font-medium">{result.optimizedHeadline}</p>
              </div>
            </div>

            {/* Summary */}
            <div className="bg-white rounded-3xl border border-slate-200 shadow-sm overflow-hidden">
              <div className="bg-slate-50 px-6 py-4 border-b border-slate-200 flex justify-between items-center">
                <h3 className="font-bold text-slate-800 flex items-center gap-2"><Linkedin className="h-4 w-4" /> Optimized About Section</h3>
                <Button variant="ghost" size="sm" onClick={() => copyToClipboard(result.optimizedSummary)}>
                  <Copy className="h-4 w-4" />
                </Button>
              </div>
              <div className="p-6">
                <p className="text-slate-700 whitespace-pre-wrap leading-relaxed">{result.optimizedSummary}</p>
              </div>
            </div>

            {/* Keywords Analysis */}
            <div className="grid grid-cols-2 gap-6">
              <div className="bg-white p-6 rounded-3xl border border-emerald-100 shadow-sm">
                <h3 className="font-bold text-emerald-800 mb-4 flex items-center gap-2"><CheckCircle2 className="h-5 w-5" /> Keywords Added</h3>
                <div className="flex flex-wrap gap-2">
                  {result.addedKeywords?.map((kw: string, i: number) => (
                    <span key={i} className="px-3 py-1 bg-emerald-50 text-emerald-700 rounded-full text-sm font-medium border border-emerald-200">{kw}</span>
                  ))}
                </div>
              </div>
              <div className="bg-white p-6 rounded-3xl border border-amber-100 shadow-sm">
                <h3 className="font-bold text-amber-800 mb-4 flex items-center gap-2"><AlertCircle className="h-5 w-5" /> Recommended Additions</h3>
                <div className="flex flex-wrap gap-2">
                  {result.missingKeywords?.map((kw: string, i: number) => (
                    <span key={i} className="px-3 py-1 bg-amber-50 text-amber-700 rounded-full text-sm font-medium border border-amber-200">{kw}</span>
                  ))}
                </div>
              </div>
            </div>
            
            {/* Analysis */}
            {result.competitiveAnalysis && (
               <div className="bg-blue-50/50 p-6 rounded-3xl border border-blue-100">
                  <h3 className="font-bold text-blue-900 mb-2">Competitive Analysis</h3>
                  <p className="text-blue-800 leading-relaxed">{result.competitiveAnalysis}</p>
               </div>
            )}
          </motion.div>
        )}
      </div>
    </div>
  );
}
