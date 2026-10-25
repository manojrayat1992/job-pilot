import { AiFeedback } from '@/components/ai/AiFeedback';
import { Button } from '@/components/ui/button';
import { useToast } from '@/hooks/use-toast';
import api from '@/lib/api';
import { motion } from 'framer-motion';
import {
ArrowLeft,
BadgeDollarSign,BarChart3,
DollarSign,
Lightbulb,
Loader2,
Shield,
Sparkles,
Target,
TrendingUp
} from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

export default function SalaryNegotiator() {
  const [form, setForm] = useState({ role: '', company: '', location: '', yearsExperience: 5, resumeId: '' });
  const [analyzing, setAnalyzing] = useState(false);
  const [result, setResult] = useState<any>(null);
  const { toast } = useToast();
  const navigate = useNavigate();

  const handleAnalyze = async () => {
    if (!form.role) {
      toast({ title: 'Role required', description: 'Please enter a job role.', variant: 'destructive' });
      return;
    }
    setAnalyzing(true);
    try {
      const res = await api.post('/api/v1/ai/salary/analyze', {
        role: form.role,
        company: form.company,
        location: form.location,
        yearsExperience: form.yearsExperience,
        resumeId: form.resumeId || null,
      });
      setResult(res.data.data);
    } catch (error: any) {
      if (error.response?.status === 402) {
        toast({ title: 'Credits Exhausted', description: 'Paid upgrades are currently unavailable.', variant: 'destructive' });
      } else {
        toast({ title: 'Analysis Failed', description: 'Please try again.', variant: 'destructive' });
      }
    } finally {
      setAnalyzing(false);
    }
  };

  const formatCurrency = (val: number) => val ? `$${val.toLocaleString()}` : 'N/A';

  return (
    <div className="min-h-screen bg-slate-50/50 pb-20 relative overflow-hidden">
      <div className="absolute top-0 left-0 w-full h-[500px] bg-gradient-to-b from-emerald-50 via-white to-transparent -z-10" />
      <div className="absolute top-[-20%] left-[-10%] w-[50%] h-[500px] bg-emerald-200/40 rounded-full blur-[120px] -z-10 mix-blend-multiply" />
      <div className="absolute top-[-10%] right-[-5%] w-[40%] h-[400px] bg-teal-200/30 rounded-full blur-[100px] -z-10 mix-blend-multiply" />

      <div className="max-w-6xl mx-auto space-y-8 px-4 pt-6">
        <div className="flex items-center justify-between">
          <Button variant="ghost" onClick={() => navigate(-1)} className="gap-2 text-slate-600 hover:text-slate-900 hover:bg-white/50 backdrop-blur-sm rounded-xl">
            <ArrowLeft className="h-4 w-4" /> Back
          </Button>
          <div className="flex items-center gap-2 text-sm font-bold text-emerald-700 bg-white/80 backdrop-blur-md px-5 py-2.5 rounded-full border border-emerald-100 shadow-sm">
            <BadgeDollarSign className="h-4 w-4 text-emerald-500" />
            <span>SALARY INTELLIGENCE</span>
          </div>
        </div>

        {!result ? (
          <div className="flex flex-col items-center text-center space-y-12 py-12">
            <div className="space-y-6">
              <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-100/50 text-emerald-700 font-medium text-sm mb-4 border border-emerald-200/50">
                <Sparkles className="h-4 w-4" /> AI Negotiation Coach
              </div>
              <h1 className="text-6xl font-black tracking-tight text-slate-900">
                Know Your <span className="text-transparent bg-clip-text bg-gradient-to-r from-emerald-600 to-teal-600">Worth</span>
              </h1>
              <p className="text-xl text-slate-600 max-w-2xl mx-auto leading-relaxed">
                Get data-driven salary analysis with specific counter-offer strategies and negotiation talking points.
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 w-full max-w-4xl">
              {[
                { icon: BarChart3, title: 'Market Data', desc: 'Levels.fyi, Glassdoor, and Blind salary ranges', color: 'emerald' },
                { icon: Target, title: 'Counter-Offer', desc: 'Specific counter-offer amounts with justification', color: 'teal' },
                { icon: Lightbulb, title: 'Talking Points', desc: 'Exact phrases to use during negotiation calls', color: 'cyan' },
              ].map((f, i) => (
                <div key={i} className="p-6 bg-white/70 backdrop-blur-xl rounded-[2rem] border border-white shadow-[0_8px_30px_rgb(0,0,0,0.04)] hover:shadow-lg transition-all duration-300 space-y-3 group">
                  <div className={`h-12 w-12 bg-gradient-to-br from-${f.color}-50 to-${f.color}-100 text-${f.color}-600 rounded-2xl flex items-center justify-center group-hover:scale-110 transition-transform duration-300`}>
                    <f.icon className="h-6 w-6" />
                  </div>
                  <h3 className="text-lg font-bold text-slate-800">{f.title}</h3>
                  <p className="text-slate-500 text-sm leading-relaxed">{f.desc}</p>
                </div>
              ))}
            </div>

            <div className="w-full max-w-2xl bg-white/70 backdrop-blur-xl rounded-[2rem] border border-white shadow-xl p-8 space-y-6">
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <label className="text-sm font-bold text-slate-700">Job Role *</label>
                  <input type="text" value={form.role} onChange={e => setForm({ ...form, role: e.target.value })}
                    placeholder="Senior Software Engineer"
                    className="w-full px-4 py-3 rounded-xl border border-slate-200 bg-white/80 focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 outline-none transition-all" />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-bold text-slate-700">Company</label>
                  <input type="text" value={form.company} onChange={e => setForm({ ...form, company: e.target.value })}
                    placeholder="Google, Meta, etc."
                    className="w-full px-4 py-3 rounded-xl border border-slate-200 bg-white/80 focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 outline-none transition-all" />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-bold text-slate-700">Location</label>
                  <input type="text" value={form.location} onChange={e => setForm({ ...form, location: e.target.value })}
                    placeholder="San Francisco, CA"
                    className="w-full px-4 py-3 rounded-xl border border-slate-200 bg-white/80 focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 outline-none transition-all" />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-bold text-slate-700">Years of Experience</label>
                  <input type="number" value={form.yearsExperience} onChange={e => setForm({ ...form, yearsExperience: parseInt(e.target.value) || 0 })}
                    className="w-full px-4 py-3 rounded-xl border border-slate-200 bg-white/80 focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 outline-none transition-all" />
                </div>
              </div>
              <Button onClick={handleAnalyze} disabled={analyzing}
                className="w-full h-14 rounded-2xl bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-700 hover:to-teal-700 text-white font-bold text-lg gap-3 shadow-lg hover:shadow-xl transition-all transform hover:-translate-y-0.5">
                {analyzing ? <><Loader2 className="h-5 w-5 animate-spin" /> Analyzing Market Data...</> : <><DollarSign className="h-5 w-5" /> Analyze Salary & Generate Strategy</>}
              </Button>
            </div>
          </div>
        ) : (
          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="space-y-8">
            <div className="flex items-center justify-between bg-white/70 backdrop-blur-xl p-6 rounded-[2rem] border border-white shadow-sm">
              <div>
                <h2 className="text-3xl font-black text-slate-900 tracking-tight">Salary Analysis</h2>
                <p className="text-slate-500 font-medium">{form.role} at {form.company || 'Market Average'} • {form.location || 'National'}</p>
              </div>
              <Button onClick={() => setResult(null)} className="rounded-full h-12 px-6 bg-slate-900 hover:bg-slate-800 text-white font-bold">
                New Analysis
              </Button>
            </div>

            {/* Salary Range Card */}
            <div className="bg-gradient-to-br from-emerald-500 to-teal-600 rounded-[2rem] p-8 text-white shadow-xl">
              <div className="flex items-center gap-2 mb-4 text-emerald-100 font-medium">
                <TrendingUp className="h-5 w-5" /> Estimated Compensation Range
              </div>
              <div className="grid grid-cols-3 gap-6">
                <div className="bg-white/10 backdrop-blur-sm rounded-2xl p-6 text-center">
                  <p className="text-emerald-100 text-sm font-medium mb-2">Minimum</p>
                  <p className="text-4xl font-black">{formatCurrency(result.estimatedMin)}</p>
                </div>
                <div className="bg-white/20 backdrop-blur-sm rounded-2xl p-6 text-center border-2 border-white/30">
                  <p className="text-emerald-100 text-sm font-medium mb-2">Target (Median)</p>
                  <p className="text-5xl font-black">{formatCurrency(result.estimatedMedian)}</p>
                </div>
                <div className="bg-white/10 backdrop-blur-sm rounded-2xl p-6 text-center">
                  <p className="text-emerald-100 text-sm font-medium mb-2">Maximum</p>
                  <p className="text-4xl font-black">{formatCurrency(result.estimatedMax)}</p>
                </div>
              </div>
              <div className="mt-4 flex items-center gap-2">
                <Shield className="h-4 w-4" />
                <span className="text-sm font-medium">Confidence: {result.confidence || 'MEDIUM'} • Currency: {result.currency || 'USD'}</span>
              </div>
            </div>

            {/* Negotiation Tips */}
            <div className="bg-white/70 backdrop-blur-xl rounded-[2rem] border border-white shadow-sm p-8">
              <h3 className="text-2xl font-black text-slate-900 mb-6 flex items-center gap-2">
                <Lightbulb className="h-6 w-6 text-amber-500" /> Negotiation Playbook
              </h3>
              <div className="space-y-3">
                {result.negotiationTips?.map((tip: string, i: number) => (
                  <motion.div key={i} initial={{ opacity: 0, x: -20 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: i * 0.1 }}
                    className="flex items-start gap-3 p-4 bg-amber-50/50 rounded-xl border border-amber-100">
                    <span className="flex-shrink-0 h-8 w-8 bg-gradient-to-br from-amber-400 to-orange-500 text-white rounded-lg flex items-center justify-center font-bold text-sm">{i + 1}</span>
                    <p className="text-slate-700 leading-relaxed">{tip}</p>
                  </motion.div>
                ))}
              </div>
            </div>

            {/* Analysis */}
            {result.analysis && (
              <div className="bg-white/70 backdrop-blur-xl rounded-[2rem] border border-white shadow-sm p-8">
                <h3 className="text-2xl font-black text-slate-900 mb-4">Detailed Analysis</h3>
                <p className="text-slate-600 leading-relaxed whitespace-pre-wrap">{result.analysis}</p>
              </div>
            )}

            {/* Data Sources */}
            {result.dataPoints?.length > 0 && (
              <div className="bg-white/70 backdrop-blur-xl rounded-[2rem] border border-white shadow-sm p-6">
                <h3 className="text-lg font-bold text-slate-700 mb-3">Data Sources</h3>
                <div className="flex flex-wrap gap-2">
                  {result.dataPoints.map((dp: string, i: number) => (
                    <span key={i} className="px-3 py-1.5 bg-slate-100 text-slate-600 rounded-full text-sm font-medium">{dp}</span>
                  ))}
                </div>
              </div>
            )}

            {/* Feedback */}
            {result.aiLogId && <AiFeedback aiLogId={result.aiLogId} />}
          </motion.div>
        )}
      </div>
    </div>
  );
}
