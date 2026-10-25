import { Button } from '@/components/ui/button';
import { useToast } from '@/hooks/use-toast';
import api from '@/lib/api';
import { motion } from 'framer-motion';
import { AlertTriangle,ArrowLeft,CheckCircle,HeartHandshake,Loader2 } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

export default function CultureFit() {
  const [form, setForm] = useState({ companyName: '', values: '' });
  const [analyzing, setAnalyzing] = useState(false);
  const [result, setResult] = useState<any>(null);
  const { toast } = useToast();
  const navigate = useNavigate();

  const handleAnalyze = async () => {
    if (!form.companyName) {
      toast({ title: 'Required', description: 'Company Name is required.', variant: 'destructive' });
      return;
    }
    setAnalyzing(true);
    try {
      const res = await api.post('/api/v1/ai/culture-fit', form);
      setResult(res.data.data);
    } catch (error: any) {
      if (error.response?.status === 402) {
        toast({ title: 'Credits Exhausted', description: 'Paid upgrades are currently unavailable.', variant: 'destructive' });
      } else {
        toast({ title: 'Failed', description: 'Please try again.', variant: 'destructive' });
      }
    } finally {
      setAnalyzing(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50/50 pb-20 relative overflow-hidden">
      <div className="max-w-6xl mx-auto space-y-8 px-4 pt-6">
        <div className="flex items-center justify-between">
          <Button variant="ghost" onClick={() => navigate(-1)} className="gap-2 text-slate-600">
            <ArrowLeft className="h-4 w-4" /> Back
          </Button>
        </div>

        {!result ? (
          <div className="flex flex-col items-center space-y-8 py-8">
            <div className="text-center max-w-2xl">
              <h1 className="text-4xl font-black text-slate-900 mb-4">Culture Fit Assessment</h1>
              <p className="text-slate-600">Find out if a company's reality matches your personal values.</p>
            </div>

            <div className="w-full max-w-xl bg-white rounded-3xl shadow-sm border border-slate-200 p-8 space-y-6">
              <div className="space-y-2">
                <label className="text-sm font-bold text-slate-700">Company Name *</label>
                <input type="text" value={form.companyName} onChange={e => setForm({ ...form, companyName: e.target.value })}
                  className="w-full px-4 py-3 rounded-xl border border-slate-200" placeholder="e.g. Netflix" />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-bold text-slate-700">Your Core Values (Optional)</label>
                <textarea value={form.values} onChange={e => setForm({ ...form, values: e.target.value })}
                  className="w-full px-4 py-3 rounded-xl border border-slate-200 resize-none" rows={3}
                  placeholder="e.g. Work-life balance, transparency, fast-paced" />
              </div>
              
              <Button onClick={handleAnalyze} disabled={analyzing} className="w-full h-14 rounded-2xl bg-slate-900 text-white font-bold">
                {analyzing ? <Loader2 className="h-5 w-5 animate-spin mr-2" /> : <HeartHandshake className="h-5 w-5 mr-2" />} Analyze Culture
              </Button>
            </div>
          </div>
        ) : (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-8 max-w-4xl mx-auto">
            <div className="bg-white rounded-3xl p-8 border border-slate-200 shadow-sm flex items-center gap-8">
              <div className="h-32 w-32 rounded-full border-8 border-indigo-50 flex items-center justify-center shrink-0">
                <span className="text-4xl font-black text-indigo-600">{result.overallScore}%</span>
              </div>
              <div>
                <h2 className="text-2xl font-black text-slate-900 mb-2">Culture Fit Verdict</h2>
                <p className="text-slate-600 leading-relaxed">{result.verdict}</p>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-6">
              <div className="bg-emerald-50 rounded-3xl p-6 border border-emerald-100">
                <h3 className="font-bold text-emerald-800 mb-4 flex items-center gap-2"><CheckCircle className="h-5 w-5" /> Green Flags</h3>
                <ul className="space-y-2">
                  {result.greenFlags?.map((f: string, i: number) => <li key={i} className="text-emerald-700 text-sm">• {f}</li>)}
                </ul>
              </div>
              <div className="bg-rose-50 rounded-3xl p-6 border border-rose-100">
                <h3 className="font-bold text-rose-800 mb-4 flex items-center gap-2"><AlertTriangle className="h-5 w-5" /> Red Flags</h3>
                <ul className="space-y-2">
                  {result.redFlags?.map((f: string, i: number) => <li key={i} className="text-rose-700 text-sm">• {f}</li>)}
                </ul>
              </div>
            </div>

            <div className="bg-white rounded-3xl p-6 border border-slate-200">
              <h3 className="font-bold text-slate-800 mb-6">Fit Breakdown</h3>
              <div className="space-y-4">
                {result.fitBreakdown?.map((fb: any, i: number) => (
                  <div key={i}>
                    <div className="flex justify-between mb-1">
                      <span className="text-sm font-medium text-slate-700">{fb.dimension}</span>
                      <span className="text-sm font-bold text-slate-900">{fb.score}/10</span>
                    </div>
                    <div className="w-full bg-slate-100 rounded-full h-2 mb-2">
                      <div className="bg-indigo-500 h-2 rounded-full" style={{ width: `${(fb.score / 10) * 100}%` }}></div>
                    </div>
                    <p className="text-xs text-slate-500">{fb.explanation}</p>
                  </div>
                ))}
              </div>
            </div>
          </motion.div>
        )}
      </div>
    </div>
  );
}
