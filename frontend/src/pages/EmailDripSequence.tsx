import { AiFeedback } from '@/components/ai/AiFeedback';
import { Button } from '@/components/ui/button';
import { useToast } from '@/hooks/use-toast';
import api from '@/lib/api';
import { motion } from 'framer-motion';
import { ArrowLeft,Clock,Linkedin,Loader2,Mail,Send } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

export default function EmailDripSequence() {
  const [form, setForm] = useState({ targetCompany: '', targetRole: '', contactName: '', contactTitle: '' });
  const [analyzing, setAnalyzing] = useState(false);
  const [result, setResult] = useState<any>(null);
  const { toast } = useToast();
  const navigate = useNavigate();

  const handleGenerate = async () => {
    if (!form.targetCompany || !form.targetRole) {
      toast({ title: 'Missing fields', description: 'Company and Role are required.', variant: 'destructive' });
      return;
    }
    setAnalyzing(true);
    try {
      const res = await api.post('/api/v1/ai/outreach/sequence', form);
      setResult(res.data.data);
    } catch (error: any) {
      if (error.response?.status === 402) {
        toast({ title: 'Credits Exhausted', description: 'Paid upgrades are currently unavailable.', variant: 'destructive' });
      } else {
        toast({ title: 'Generation Failed', description: 'Please try again.', variant: 'destructive' });
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
          <div className="flex items-center gap-2 text-sm font-bold text-violet-700 bg-violet-100 px-5 py-2.5 rounded-full">
            <Mail className="h-4 w-4" /> OUTREACH AUTOMATION
          </div>
        </div>

        {!result ? (
          <div className="flex flex-col items-center space-y-8 py-8">
            <div className="text-center max-w-2xl">
              <h1 className="text-4xl font-black text-slate-900 mb-4">Generate Drip Sequence</h1>
              <p className="text-slate-600">Create a 5-step strategic outreach campaign to connect with decision-makers.</p>
            </div>

            <div className="w-full max-w-2xl bg-white rounded-3xl shadow-sm border border-slate-200 p-8 space-y-6">
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <label className="text-sm font-bold text-slate-700">Target Company *</label>
                  <input type="text" value={form.targetCompany} onChange={e => setForm({ ...form, targetCompany: e.target.value })}
                    className="w-full px-4 py-3 rounded-xl border border-slate-200" />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-bold text-slate-700">Target Role *</label>
                  <input type="text" value={form.targetRole} onChange={e => setForm({ ...form, targetRole: e.target.value })}
                    className="w-full px-4 py-3 rounded-xl border border-slate-200" />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-bold text-slate-700">Contact Name (Optional)</label>
                  <input type="text" value={form.contactName} onChange={e => setForm({ ...form, contactName: e.target.value })}
                    className="w-full px-4 py-3 rounded-xl border border-slate-200" />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-bold text-slate-700">Contact Title (Optional)</label>
                  <input type="text" value={form.contactTitle} onChange={e => setForm({ ...form, contactTitle: e.target.value })}
                    className="w-full px-4 py-3 rounded-xl border border-slate-200" />
                </div>
              </div>
              <Button onClick={handleGenerate} disabled={analyzing}
                className="w-full h-14 rounded-2xl bg-violet-600 hover:bg-violet-700 text-white font-bold text-lg">
                {analyzing ? <Loader2 className="h-5 w-5 animate-spin mr-2" /> : <Send className="h-5 w-5 mr-2" />} 
                {analyzing ? 'Generating Sequence...' : 'Generate 5-Step Campaign'}
              </Button>
            </div>
          </div>
        ) : (
          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="space-y-8 max-w-4xl mx-auto">
            <h2 className="text-3xl font-black text-slate-900">Campaign Sequence</h2>
            
            <div className="space-y-6 relative before:absolute before:inset-0 before:ml-6 before:-translate-x-px md:before:mx-auto md:before:translate-x-0 before:h-full before:w-1 before:bg-gradient-to-b before:from-violet-500 before:to-slate-200">
              
              {/* Step 1: Initial */}
              <div className="relative flex items-center justify-between md:justify-normal md:odd:flex-row-reverse group is-active">
                <div className="flex items-center justify-center w-12 h-12 rounded-full border-4 border-white bg-violet-500 text-white shadow shrink-0 md:order-1 md:group-odd:-translate-x-1/2 md:group-even:translate-x-1/2 z-10">
                  <Mail className="w-5 h-5" />
                </div>
                <div className="w-[calc(100%-4rem)] md:w-[calc(50%-3rem)] bg-white p-6 rounded-2xl shadow-sm border border-slate-200">
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-bold text-violet-600">Day 1</span>
                    <span className="text-xs font-semibold px-2 py-1 bg-slate-100 rounded-full">Cold Email</span>
                  </div>
                  <h3 className="font-bold text-slate-900 mb-2">{result.subject}</h3>
                  <p className="text-slate-600 text-sm whitespace-pre-wrap">{result.body}</p>
                </div>
              </div>

              {/* Step 2: LinkedIn */}
              <div className="relative flex items-center justify-between md:justify-normal md:odd:flex-row-reverse group is-active">
                <div className="flex items-center justify-center w-12 h-12 rounded-full border-4 border-white bg-[#0A66C2] text-white shadow shrink-0 md:order-1 md:group-odd:-translate-x-1/2 md:group-even:translate-x-1/2 z-10">
                  <Linkedin className="w-5 h-5" />
                </div>
                <div className="w-[calc(100%-4rem)] md:w-[calc(50%-3rem)] bg-white p-6 rounded-2xl shadow-sm border border-slate-200">
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-bold text-blue-600">Day 1 (Later)</span>
                    <span className="text-xs font-semibold px-2 py-1 bg-slate-100 rounded-full">Connection Request</span>
                  </div>
                  <p className="text-slate-600 text-sm whitespace-pre-wrap">{result.linkedInMessage}</p>
                </div>
              </div>

              {/* Follow Ups */}
              {result.followUpSequence?.map((fu: any, i: number) => (
                <div key={i} className="relative flex items-center justify-between md:justify-normal md:odd:flex-row-reverse group">
                  <div className="flex items-center justify-center w-12 h-12 rounded-full border-4 border-white bg-slate-200 text-slate-500 shadow shrink-0 md:order-1 md:group-odd:-translate-x-1/2 md:group-even:translate-x-1/2 z-10">
                    <Clock className="w-5 h-5" />
                  </div>
                  <div className="w-[calc(100%-4rem)] md:w-[calc(50%-3rem)] bg-white p-6 rounded-2xl shadow-sm border border-slate-200">
                    <div className="flex items-center justify-between mb-2">
                      <span className="font-bold text-slate-700">Day {fu.dayAfter}</span>
                      <span className="text-xs font-semibold px-2 py-1 bg-slate-100 rounded-full">{fu.channel}</span>
                    </div>
                    {fu.subject && <h3 className="font-bold text-slate-900 mb-2">{fu.subject}</h3>}
                    <p className="text-slate-600 text-sm whitespace-pre-wrap">{fu.body}</p>
                  </div>
                </div>
              ))}
            </div>

            {/* Feedback */}
            {result.aiLogId && <AiFeedback aiLogId={result.aiLogId} />}

          </motion.div>
        )}
      </div>
    </div>
  );
}
