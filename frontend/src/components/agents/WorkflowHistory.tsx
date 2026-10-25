import { useEffect, useState } from 'react';
import api from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';

interface SavedRun {
  workflowId: string;
  userGoal: string;
  status: string;
  createdAt: string;
}

export default function WorkflowHistory({ refreshKey, disabled, onOpen }: {
  refreshKey: number;
  disabled: boolean;
  onOpen: (id: string) => void;
}) {
  const [runs, setRuns] = useState<SavedRun[]>([]);
  const [page, setPage] = useState(0);
  const [refresh, setRefresh] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    api.get('/api/v1/agents/workflows', { params: { page } })
      .then(response => { if (active) setRuns(response.data.data); })
      .catch(() => { if (active) setError('Unable to load saved workflows. Please refresh.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [page, refresh, refreshKey]);
  return (
    <Card className="border-slate-200 shadow-sm rounded-3xl">
      <CardContent className="p-6 space-y-4">
        <div className="flex items-center justify-between gap-2">
          <h3 className="font-bold text-slate-800">Saved workflows</h3>
          <Button variant="ghost" size="sm" disabled={loading} onClick={() => setRefresh(value => value + 1)}>Refresh</Button>
        </div>
        <p className="text-xs text-slate-500">Reopen drafts and resume unfinished work. Outputs are drafts for your review.</p>
        {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
        {loading && <p className="text-sm text-slate-500">Loading history…</p>}
        {!loading && !error && runs.length === 0 && <p className="text-sm text-slate-500">No saved workflows on this page.</p>}
        <div className="space-y-2">
          {runs.map(run => (
            <button key={run.workflowId} disabled={disabled || loading} onClick={() => onOpen(run.workflowId)}
              className="w-full rounded-xl border border-slate-200 p-3 text-left hover:bg-slate-50 disabled:opacity-50">
              <p className="text-sm font-medium line-clamp-2">{run.userGoal}</p>
              <p className="mt-1 text-xs text-slate-500">{run.status} · {new Date(run.createdAt).toLocaleString()}</p>
            </button>
          ))}
        </div>
        <div className="flex items-center justify-between">
          <Button variant="outline" size="sm" disabled={loading || page === 0} onClick={() => setPage(value => value - 1)}>Previous</Button>
          <span className="text-xs text-slate-500">Page {page + 1}</span>
          <Button variant="outline" size="sm" disabled={loading || runs.length < 20} onClick={() => setPage(value => value + 1)}>Next</Button>
        </div>
      </CardContent>
    </Card>
  );
}
