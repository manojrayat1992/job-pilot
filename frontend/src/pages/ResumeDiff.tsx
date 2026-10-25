import { Button } from '@/components/ui/button';
import api from '@/lib/api';
import { ArrowLeft,GitCompare,Loader2 } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

export default function ResumeDiff() {
  const [form, setForm] = useState({ originalText: '', tailoredText: '' });
  const [analyzing, setAnalyzing] = useState(false);
  const [result, setResult] = useState<any>(null);
  const navigate = useNavigate();

  const handleDiff = async () => {
    setAnalyzing(true);
    try {
      const res = await api.post('/api/v1/ai/resume/diff', form);
      setResult(res.data.data);
    } finally {
      setAnalyzing(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50/50 pb-20">
      <div className="max-w-7xl mx-auto space-y-8 px-4 pt-6">
        <Button variant="ghost" onClick={() => navigate(-1)}>
          <ArrowLeft className="h-4 w-4 mr-2" /> Back
        </Button>

        {!result ? (
          <div className="space-y-6">
            <h1 className="text-3xl font-black">Resume Version Diff</h1>
            <div className="grid grid-cols-2 gap-4">
              <textarea value={form.originalText} onChange={e => setForm({...form, originalText: e.target.value})}
                placeholder="Original Resume Text..." className="h-96 p-4 rounded-xl border" />
              <textarea value={form.tailoredText} onChange={e => setForm({...form, tailoredText: e.target.value})}
                placeholder="Tailored Resume Text..." className="h-96 p-4 rounded-xl border" />
            </div>
            <Button onClick={handleDiff} disabled={analyzing} className="w-full">
              {analyzing ? <Loader2 className="animate-spin" /> : <GitCompare className="mr-2" />} Compare Versions
            </Button>
          </div>
        ) : (
          <div className="space-y-6">
            <div className="flex gap-4 p-4 bg-white rounded-xl shadow-sm">
              <div className="text-green-600 font-bold">+{result.stats.additions} Additions</div>
              <div className="text-red-600 font-bold">-{result.stats.removals} Removals</div>
              <div className="text-slate-600 font-bold">{result.stats.changePercentage}% Changed</div>
            </div>
            <div className="bg-white border rounded-xl overflow-hidden text-sm font-mono">
              {result.diff.map((line: any, i: number) => (
                <div key={i} className={`px-4 py-1 flex ${line.type === 'added' ? 'bg-green-50 text-green-800' : line.type === 'removed' ? 'bg-red-50 text-red-800' : 'text-slate-600'}`}>
                  <span className="w-8 shrink-0 text-slate-400 select-none border-r border-slate-200 mr-4">
                    {line.type === 'added' ? '+' : line.type === 'removed' ? '-' : ' '}
                  </span>
                  <span className="whitespace-pre-wrap">{line.content}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
